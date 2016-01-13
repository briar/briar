#!/bin/sh
sed -i "s@include ':briar-android'@//include ':briar-android'@" settings.gradle
gradle test
sed -i "s@//include ':briar-android'@include ':briar-android'@" settings.gradle
