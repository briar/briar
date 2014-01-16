#!/bin/bash

dirs=(ant core docs jce mail pg pkix prov)

for file in `find ${dirs[@]} -name bouncycastle`
do
	path=`dirname $file`
	echo "Moving $file to $path/spongycastle"
	mv $file $path/spongycastle
done

for file in `grep -Rl bouncycastle ${dirs[@]}`
do
	echo "Replacing string bouncycastle in $file"
	sed -i 's/bouncycastle/spongycastle/g' $file
done

