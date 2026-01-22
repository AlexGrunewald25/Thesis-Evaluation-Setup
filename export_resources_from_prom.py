#!/usr/bin/env python3
"""
Export resource metrics (CPU/RAM) from Prometheus for a given time range.

This variant supports cAdvisor setups where container identity is exposed via:
  label: id="/docker/<container-id>"

It also supports mapping Docker container IDs to names/services via an external
JSON file (recommended on Windows Git-Bash setups), because calling docker from
WindowsStore python can be unreliable.

Output:
- containers: per container-id stats (avg/p95/max)
- groups: aggregated per service (preferred) or per name/id fallback
"""

from __future__ import annotations

import argparse
import json
import math
import re
import urllib.parse
import urllib.request
from typing import Any, Dict, List, Tuple, Optional


DOCKER_ID_RE = re.compile(r"^/docker/([0-9a-f]{12,64})$")


def prom_query_range(prom_url: str, query: str, start: int, end: int, step: str) -> Dict[str, Any]:
    params = {"query": query, "start": str(start), "end": str(end), "step": step}
    url = f"{prom_url.rstrip('/')}/api/v1/query_range?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=90) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    if data.get("status") != "success":
        raise RuntimeError(f"Prometheus error: {data}")
    return data


def percentile(values: List[float], p: float) -> float:
    if not values:
        return float("nan")
    vs = sorted(values)
    if len(vs) == 1:
        return vs[0]
    idx = (len(vs) - 1) * p
    lo = int(math.floor(idx))
    hi = int(math.ceil(idx))
    if lo == hi:
        return vs[lo]
    w = idx - lo
    return vs[lo] * (1 - w) + vs[hi] * w


def stats(values: List[float]) -> Dict[str, float]:
    if not values:
        return {"avg": float("nan"), "p95": float("nan"), "max": float("nan")}
    return {"avg": sum(values) / len(values), "p95": percentile(values, 0.95), "max": max(values)}


def parse_matrix(matrix: Dict[str, Any], label_key: str) -> Dict[str, List[Tuple[int, float]]]:
    out: Dict[str, List[Tuple[int, float]]] = {}
    for series in matrix.get("data", {}).get("result", []):
        metric = series.get("metric", {})
        key = metric.get(label_key)
        if not key:
            continue
        pts: List[Tuple[int, float]] = []
        for ts_str, v_str in series.get("values", []):
            try:
                ts = int(float(ts_str))
                v = float(v_str)
            except Exception:
                continue
            if math.isfinite(v):
                pts.append((ts, v))
        if pts:
            out[key] = pts
    return out


def cgroup_id_to_docker_id12(cgroup_id: str) -> Optional[str]:
    m = DOCKER_ID_RE.match(cgroup_id)
    if not m:
        return None
    full = m.group(1)
    return full[:12]


def aggregate_by_group(
    series_by_cgroup: Dict[str, List[Tuple[int, float]]],
    group_of_cgroup: Dict[str, str],
) -> Dict[str, List[float]]:
    group_ts_sum: Dict[str, Dict[int, float]] = {}
    for cgroup_id, points in series_by_cgroup.items():
        group = group_of_cgroup.get(cgroup_id)
        if not group:
            continue
        bucket = group_ts_sum.setdefault(group, {})
        for ts, v in points:
            bucket[ts] = bucket.get(ts, 0.0) + v

    out: Dict[str, List[float]] = {}
    for group, ts_map in group_ts_sum.items():
        out[group] = [val for _, val in sorted(ts_map.items(), key=lambda x: x[0])]
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--prom-url", required=True)
    ap.add_argument("--start", type=int, required=True)
    ap.add_argument("--end", type=int, required=True)
    ap.add_argument("--step", default="10s")
    ap.add_argument("--test-run", required=True)
    ap.add_argument("--out", required=True)

    ap.add_argument("--service-regex", default="claim-service|policy-service|customer-service|postgres|kafka|zookeeper")
    ap.add_argument("--job", default="cadvisor")
    ap.add_argument("--docker-map", default="", help="JSON file produced by run-loadtest.sh (docker ps mapping)")

    args = ap.parse_args()

    job_filter = f'job="{args.job}",' if args.job else ""
    cgroup_filter = 'id=~"/docker/[0-9a-f]{12,64}"'

    cpu_q = (
        'sum by (id) ('
        f'rate(container_cpu_usage_seconds_total{{{job_filter}cpu="total",{cgroup_filter}}}[1m])'
        ")"
    )
    mem_q = (
        'max by (id) ('
        f'container_memory_working_set_bytes{{{job_filter}{cgroup_filter}}}'
        ")"
    )

    cpu_matrix = prom_query_range(args.prom_url, cpu_q, args.start, args.end, args.step)
    mem_matrix = prom_query_range(args.prom_url, mem_q, args.start, args.end, args.step)

    cpu_series = parse_matrix(cpu_matrix, "id")
    mem_series = parse_matrix(mem_matrix, "id")

    # Load docker map if provided
    docker_map: Dict[str, Dict[str, str]] = {}
    if args.docker_map:
        with open(args.docker_map, "r", encoding="utf-8") as f:
            docker_map = json.load(f)

    service_re = re.compile(args.service_regex) if args.service_regex else None

    containers: Dict[str, Any] = {}
    cgroup_to_group: Dict[str, str] = {}

    all_cgroups = sorted(set(list(cpu_series.keys()) + list(mem_series.keys())))
    for cgroup_id in all_cgroups:
        did12 = cgroup_id_to_docker_id12(cgroup_id) or cgroup_id

        meta = docker_map.get(did12, {})
        name = meta.get("name")
        service = meta.get("service")

        # grouping preference: service -> name -> docker id
        group = service or name or did12

        cgroup_to_group[cgroup_id] = group

        containers[did12] = {
            "docker_id": did12 if isinstance(did12, str) else None,
            "cgroup_id": cgroup_id,
            "name": name,
            "service": service,
            "cpu_cores": stats([v for _, v in cpu_series.get(cgroup_id, [])]),
            "mem_bytes": stats([v for _, v in mem_series.get(cgroup_id, [])]),
        }

    cpu_group_series = aggregate_by_group(cpu_series, cgroup_to_group)
    mem_group_series = aggregate_by_group(mem_series, cgroup_to_group)

    groups: Dict[str, Any] = {}
    for group in sorted(set(list(cpu_group_series.keys()) + list(mem_group_series.keys()))):
        if service_re and not service_re.search(group):
            continue
        groups[group] = {
            "cpu_cores": stats(cpu_group_series.get(group, [])),
            "mem_bytes": stats(mem_group_series.get(group, [])),
        }

    out = {
        "test_run": args.test_run,
        "start_epoch": args.start,
        "end_epoch": args.end,
        "step": args.step,
        "prometheus": {"url": args.prom_url, "job": args.job, "queries": {"cpu": cpu_q, "mem": mem_q}},
        "labeling": {
            "series_label_key": "id",
            "cgroup_pattern": "/docker/<container-id>",
            "grouping": "service (from docker ps map) -> name -> docker id",
        },
        "containers": containers,
        "groups": groups,
    }

    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2, ensure_ascii=False)


if __name__ == "__main__":
    main()
