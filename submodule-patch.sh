#!/bin/bash

# patch required to use briar as a gradle subproject (e.g. in brair-desktop):
# 1. git merge <latest-release-tag>
# 2. git checkout --theirs .
# 3. git add .
# 4. git merge --continue
# 5. ./submodule-patch.sh
# 6. git add .
# 7. git commit -m "apply patch"

sed -i "s/':bramble-/':briar:bramble-/g" bramble-{android,core,java}/build.gradle
sed -i "s/':bramble-/':briar:bramble-/g" briar-{android,core,api}/build.gradle
sed -i "s/':briar-/':briar:briar-/g" briar-{android,core,api}/build.gradle
