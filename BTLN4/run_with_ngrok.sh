#!/bin/bash
# =============================================================================
# run_with_ngrok.sh – Chạy ứng dụng đấu giá với ngrok
#push
# Cách dùng:
#   1. Chạy server (máy A – người chạy server):
#        ./run_with_ngrok.sh server
#
#   2. Chạy client kết nối ngrok (máy B):
#        ./run_with_ngrok.sh client ws://0.tcp.ngrok.io:XXXXX/auction
#
#   3. Chạy local (cùng máy, test):
#        ./run_with_ngrok.sh local
# =============================================================================

MODE=${1:-"local"}
NGROK_URL=${2:-""}

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$PROJECT_DIR/target/app-1.0-SNAPSHOT.jar"

# Build nếu chưa có jar
if [ ! -f "$JAR" ]; then
    echo ">>> Building project..."
    cd "$PROJECT_DIR" && mvn package -q -DskipTests
fi

case "$MODE" in
    server|local)
        echo "==================================================="
        echo "  Khởi động SERVER + UI (localhost mode)"
        echo "==================================================="
        if command -v ngrok &>/dev/null; then
            echo ""
            echo "  ngrok đã được cài. Để expose ra ngoài:"
            echo "    ngrok tcp 7000"
            echo "  Sau đó client khác chạy:"
            echo "    ./run_with_ngrok.sh client ws://<ngrok-host>:<port>/auction"
            echo ""
        fi
        cd "$PROJECT_DIR"
        mvn javafx:run
        ;;

    client)
        if [ -z "$NGROK_URL" ]; then
            echo "Lỗi: Cần cung cấp ngrok URL."
            echo "Ví dụ: ./run_with_ngrok.sh client ws://0.tcp.ngrok.io:12345/auction"
            exit 1
        fi
        echo "==================================================="
        echo "  Khởi động CLIENT kết nối: $NGROK_URL"
        echo "==================================================="
        cd "$PROJECT_DIR"
        mvn javafx:run -Djavafx.application.args="" \
            -Djvm.args="-Dauction.server.url=$NGROK_URL"
        ;;

    *)
        echo "Cách dùng: $0 [server|client|local] [ngrok_url]"
        exit 1
        ;;
esac
