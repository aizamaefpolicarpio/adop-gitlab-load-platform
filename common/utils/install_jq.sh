#!/bin/bash 

set -e

if [[ ! -f "$HOME/bin/jq" ]]; then
	echo "Installing JQ .."
	mkdir -p $HOME/bin/
	curl -Lo "$HOME/bin/jq" "https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux64" && chmod +x "$HOME/bin/jq"
fi