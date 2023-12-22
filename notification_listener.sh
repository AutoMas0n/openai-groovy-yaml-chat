#!/bin/bash
#Lazy: This script is unrelated and will need to go to a different repo
#sudo apt install kdialog

dbus-monitor "interface='org.freedesktop.Notifications'" | \
(
    # State variables
    title=""
    body=""
    app_name=""
    app_name_captured=false
    count=0

    while read -r line; do
        if [[ $line == *"member=Notify"* ]]; then
            # Reset state
            count=0
            app_name_captured=false
            title=""
            body=""
        elif [[ $line == *"string"* ]]; then
            ((count++))
            if [ $count -eq 1 ]; then
                app_name=$(echo "$line" | sed -n 's/.*string "\(.*\)".*/\1/p')
                app_name_captured=true
            elif [ $count -eq 3 ] && $app_name_captured; then
                title=$(echo "$line" | sed -n 's/.*string "\(.*\)".*/\1/p')
            elif [ $count -eq 4 ] && $app_name_captured; then
                body=$(echo "$line" | sed -n 's/.*string "\(.*\)".*/\1/p')

                # Check if the title should be ignored
                if [[ $title != "Activate" && $title != "suppress-sound" && $title != "desktop-entry" ]]; then
                    # Output the title and body
                    echo "Title: $title"
                    echo "Body: $body"
                    echo # Extra line for spacing between notifications
                    kdeconnect-cli --ping-msg "$title / $body" -n "Pixel 7 Pro"
                fi

                # Reset state
                count=0
                app_name_captured=false
                title=""
                body=""
            fi
        fi
    done
)