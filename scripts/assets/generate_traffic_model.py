#!/usr/bin/env python3
"""Compatibility entry point for the production AIS fleet asset generator."""
from pathlib import Path
import runpy

runpy.run_path(str(Path(__file__).with_name('generate_traffic_fleet.py')), run_name='__main__')
