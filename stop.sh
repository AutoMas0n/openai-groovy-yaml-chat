#!/bin/bash

# Simulate the keypress Ctrl+Shift+P
xdotool key --clearmodifiers ctrl+shift+P

# Wait for the command palette to open
sleep 1

# Type 'Tasks: Run Task' and press Enter
xdotool type --clearmodifiers 'Live Share: Stop Collaboration Session'
sleep 1
xdotool key --clearmodifiers Return

