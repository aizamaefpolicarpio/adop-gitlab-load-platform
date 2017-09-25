#!/bin/bash 

set -e

if [[ ! -f "/usr/local/bin/jq" ]]; then
	echo "Installing JQ .."
	curl -Lo "/usr/local/bin/jq" "https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux64" && chmod +x "/usr/local/bin/jq"
fi