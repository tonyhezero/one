#! /bin/sh
java -Xmx512M -cp .:lib/ECLA.jar:lib/DTNConsoleConnection.jar:lib/junit-4.8.2.jar:lib/uncommons-maths-1.2.1.jar core.DTNSim $*
