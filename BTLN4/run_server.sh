#!/bin/bash
# =============================================================================
# run_server.sh – Chạy Auction WebSocket Server (không có giao diện)
#
# Chạy trên máy chủ (của bạn):
#   chmod +x run_server.sh
#   ./run_server.sh
#
# Sau khi server khởi động, expose ra ngoài bằng ngrok:
#   ngrok http 7000
#
# Khi URL ngrok thay đổi:
#   1. Cập nhật DEFAULT_URL trong ServerConfig.java
#   2. Build lại client JAR:  mvn package -DskipTests -Dcheckstyle.skip=true
#   3. Chia sẻ file app-1.0-SNAPSHOT.jar mới cho bạn bè
# =============================================================================

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$PROJECT_DIR/target/app-1.0-SNAPSHOT.jar"

# Build if JAR doesn't exist yet
if [ ! -f "$JAR" ]; then
    echo ">>> Building project (first time)..."
    cd "$PROJECT_DIR" && mvn package -q -DskipTests -Dcheckstyle.skip=true
fi

echo "==================================================="
echo "  Auction Server – port 7000"
echo "==================================================="
echo ""

cd "$PROJECT_DIR"
# Run ServerMain directly from the fat JAR
# The -Dauction.server.url override makes the server print localhost (correct)
java \
    -Dauction.server.url=ws://localhost:7000/auction \
    -cp "$JAR" \
    com.auction.ServerMain
