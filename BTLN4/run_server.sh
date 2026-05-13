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

echo "==================================================="
echo "  Auction Server – port 7000"
echo "==================================================="
echo ""

cd "$PROJECT_DIR"

# Always compile + run via Maven so that new classes (ServerMain) are included.
# The 'server' profile activates exec-maven-plugin pointing to ServerMain.
mvn compile exec:java \
    -Pserver \
    -Dcheckstyle.skip=true \
    -Dauction.server.url=ws://localhost:7000/auction \
    -q
