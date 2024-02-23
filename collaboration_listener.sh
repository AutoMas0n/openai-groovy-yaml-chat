#!/bin/bash

# Initialize a variable to keep track of the collaboration state
collaboration_active=false

while true; do
  # Listen on TCP port 9113 for incoming connections
  { 
    # Send a minimal HTTP response and close the connection
    echo -ne "HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
  } | nc -l -p 9113 | while read line; do
    # Check if the line is empty (end of HTTP headers)
    if [ -z "$line" ]; then
        # Simulate the keypress Ctrl+Shift+P
        xdotool key --clearmodifiers ctrl+shift+P

        # Wait for the command palette to open
        sleep 1

        if [ "$collaboration_active" = false ]; then
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
            collaboration_active=true
        else
            echo "Stopping collaboration session!"
            # Type 'Live Share: Stop Collaboration Session' and press Enter to stop collaboration session
            xdotool type --clearmodifiers 'Live Share: Stop Collaboration Session'
            sleep 1
            xdotool key --clearmodifiers Return

            # Set the flag to false as the collaboration session is now stopped
            collaboration_active=false
        fi

      # Break out of the loop to go back to listening for connections
      break
    else
      # Output the HTTP request line for debugging purposes
      echo "$line"
    fi
  done
done