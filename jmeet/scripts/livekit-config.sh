#!/bin/sh

# Run this script to generate docker/livekit/livekit.prod.yaml and docker/livekit/egress.prod.yaml from the templates, substituting in the values from .env.
set -eu

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  echo "no .env file found next to compose.prod.yml" >&2
  exit 1
fi

set -a
. ./.env
set +a

for name in livekit egress; do
  envsubst '${LIVEKIT_API_KEY} ${LIVEKIT_API_SECRET}' \
    < "docker/livekit/${name}.yaml.template" \
    > "docker/livekit/${name}.prod.yaml"
done

echo "wrote docker/livekit/livekit.prod.yaml and docker/livekit/egress.prod.yaml"
