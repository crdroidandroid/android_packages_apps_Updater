#!/bin/bash
grep -rl "The LineageOS" res/. | xargs sed -i 's/The LineageOS/The LOS/g'
grep -rl "LineageOS" res/. | xargs sed -i 's/LineageOS/crDroid/g'
grep -rl "The LOS" res/. | xargs sed -i 's/The LOS/The LineageOS/g'
