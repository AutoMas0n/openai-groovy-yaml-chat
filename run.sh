#!/bin/bash

# Simulate the keypress Ctrl+Shift+P
xdotool key --clearmodifiers ctrl+shift+P

# Wait for the command palette to open
sleep 1

# Type 'Tasks: Run Task' and press Enter
xdotool type --clearmodifiers 'Tasks: Run Task'
xdotool key --clearmodifiers Return

# Wait for the tasks list to open
sleep 1

# Type 'Share Server' and press Enter
xdotool type --clearmodifiers 'Share Server'
xdotool key --clearmodifiers Return

# Wait for 10 seconds as instructed
sleep 4

# Type '9001' and wait 2 seconds
xdotool type --clearmodifiers '9001'
sleep 2

# Press Enter
xdotool key --clearmodifiers Return

# Press Enter
xdotool key --clearmodifiers Return

echo -e "Subject: New VS URL\n\n$(xclip -selection clipboard -o)" | msmtp -a gmail jgnotifier@gmail.com