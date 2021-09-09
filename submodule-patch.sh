#!/bin/bash

sed -i "s/':bramble-/':briar:bramble-/g" bramble-{android,core,java}/build.gradle
sed -i "s/':bramble-/':briar:bramble-/g" briar-{android,core,api}/build.gradle
sed -i "s/':briar-/':briar:bria-/g" briar-{android,core,api}/build.gradle
