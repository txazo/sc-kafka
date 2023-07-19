#/bin/bash
ps -ef | grep 'Gradle' | grep -v grep | awk '{print $2}' | xargs kill -9
ps -ef | grep 'kafka' | grep -v grep | awk '{print $2}' | xargs kill -9
