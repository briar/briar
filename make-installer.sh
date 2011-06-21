#!/bin/bash

#FIXME: Replace this with an ant script

rm -rf temp briar.zip
mkdir temp
cd bin
for dir in api/i18n api/setup i18n setup util ui/setup ui/wizard
do
	mkdir -p ../temp/net/sf/briar/$dir
	cp net/sf/briar/$dir/*.class ../temp/net/sf/briar/$dir
done
jar cf ../temp/main.jar net *.properties
cd ..
cp i18n/*.properties i18n/*.ttf temp
cp lib/*.jar temp
cp -r windows-jre temp/jre
cp lib/setup.vbs temp
mkdir temp/META-INF
cp lib/installer.manifest temp/META-INF/MANIFEST.MF
cd temp
echo '$AUTORUN$>start /b briar.tmp\\setup.vbs' | zip -z -r ../briar.zip META-INF net jre *.jar *.properties *.ttf setup.vbs
cd ..
cat lib/unzipsfx.exe briar.zip > briar.exe
rm -rf temp briar.zip
