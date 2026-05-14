#!/bin/bash

# find the process ID (PID) listening on port 7000
PID=$(lsof -ti:7000)

if [ -z "$PID" ]; then
    echo "No server found running on port 7000."
else
    echo "Stopping server (PID: $PID)..."
    kill -9 $PID
    echo "Server stopped successfully."
fi
