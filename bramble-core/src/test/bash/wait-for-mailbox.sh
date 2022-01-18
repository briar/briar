#!/bin/bash
set -e

URL="http://127.0.0.1:8000/status"
attempt_counter=0
max_attempts=200 # 10min - CI for mailbox currently takes ~5min

echo "Waiting for mailbox to come online at $URL"

until [[ "$(curl -s -o /dev/null -w '%{http_code}' $URL)" == "401" ]]; do
  if [ ${attempt_counter} -eq ${max_attempts} ]; then
    echo "Timed out waiting for mailbox"
    exit 1
  fi

  printf '.'
  attempt_counter=$((attempt_counter + 1))
  sleep 3
done

echo "Mailbox started"
