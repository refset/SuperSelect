#!/usr/bin/env bash

set -e

pushd `dirname "${BASH_SOURCE[0]}"` > /dev/null
source "./config.sh"

pushd "$ROOT"

if [ ! -d "$BROWSER_USER_PROFILE" ] ; then
  mkdir -p "$BROWSER_USER_PROFILE"
fi

EXE="/opt/google/chrome/google-chrome"

# we want wait to compilation of our extensions, so that --load-extension param does not fail

#SECS_TO_WAIT=120
#echo "waiting $SECS_TO_WAIT seconds before launching Chrome to give cljs compiler time to build the extension"
#sleep "$SECS_TO_WAIT"

echo "launching Chrome from $EXE"

set -x
"$EXE" \
      --user-data-dir="$BROWSER_USER_PROFILE" \
      --no-first-run \
      --enable-experimental-extension-apis \
      --disk-cache-dir=/dev/null \
      --media-cache-dir=/dev/null \
      --disable-hang-monitor \
      --disable-prompt-on-repost \
      --dom-automation \
      --full-memory-crash-report \
      --no-default-browser-check \
      --auto-open-devtools-for-tabs \
      --load-extension="$DEV_EXTENSION_PATH"

set +x

popd
