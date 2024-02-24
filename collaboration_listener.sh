#!/bin/bash

# Initialize a variable to keep track of the collaboration state
state_file="/tmp/collaboration_state"

# Ensure state file exists
echo "false" > "$state_file"

while true; do
  # Start Python HTTP server and wait for a connection
  python3 -c "import http.server; http.server.HTTPServer(('', 9113), lambda *args: type('Handler', (http.server.BaseHTTPRequestHandler,), dict(do_GET=lambda self: self.send_response(200)))()).handle_request()"

  collaboration_active=$(cat "$state_file")
  # Simulate the keypress Ctrl+Shift+P
  xdotool key --clearmodifiers ctrl+shift+P
  sleep 2
  xdotool key --clearmodifiers ctrl+shift+P
  sleep 1

  if [ "$collaboration_active" = "false" ]; then
      echo "Starting collaboration session!"
      # Type 'Tasks: Run Task' and press Enter to start collaboration session
      xdotool type --clearmodifiers 'Tasks: Run Task'
      xdotool key --clearmodifiers Return

      # Wait for the tasks list to open
      sleep 1

      # Type 'Share Server' and press Enter
      xdotool type --clearmodifiers 'Share Server'
      xdotool key --clearmodifiers Return

      # Wait for 4 seconds as instructed
      sleep 4

      # Type '9001' and press Enter
      xdotool type --clearmodifiers '9001'
      sleep 2

      # Press Enter
      xdotool key --clearmodifiers Return

      # Press Enter
      xdotool key --clearmodifiers Return

      echo -e "Subject: New VS URL\n\n$(xclip -selection clipboard -o)" | msmtp -a gmail jgnotifier@gmail.com

      # Set the flag to true as the collaboration session is now active
      echo "true" > "$state_file"
  else
      echo "Stopping collaboration session!"
      # Type 'Live Share: Stop Collaboration Session' and press Enter to stop collaboration session
      xdotool type --clearmodifiers 'Live Share: Stop Collaboration Session'
      sleep 1
      xdotool key --clearmodifiers Return

      # Set the flag to false as the collaboration session is now stopped
      echo "false" > "$state_file"
  fi
done