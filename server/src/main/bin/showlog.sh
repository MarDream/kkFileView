#!/bin/bash
LOG_PATH="${LOG_PATH:-$(cd "$(dirname "$0")" || exit 1 ; pwd)/../log}"
tail -fn 300 "${LOG_PATH}/kkFileView.log"
