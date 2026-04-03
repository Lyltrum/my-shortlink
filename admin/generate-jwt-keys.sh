#!/bin/bash
# ==================================================================
# 生成 RSA 密钥对脚本
# 用法: bash generate-jwt-keys.sh
#       或直接在 IDE 中运行 JwtKeyGenerator.java main 方法
# ==================================================================

DIR="src/main/resources/keys"
PUBLIC_KEY_PATH="$DIR/public.pem"
PRIVATE_KEY_PATH="$DIR/private.pem"

mkdir -p "$DIR"

if [ -f "$PUBLIC_KEY_PATH" ] && [ -f "$PRIVATE_KEY_PATH" ]; then
    echo "密钥文件已存在: $PUBLIC_KEY_PATH 和 $PRIVATE_KEY_PATH"
    echo "如需重新生成，请先删除这两个文件。"
    exit 0
fi

echo "正在生成 RSA-2048 密钥对..."

# 使用 OpenSSL 生成密钥对
openssl genrsa -out "$PRIVATE_KEY_PATH" 2048
openssl rsa -in "$PRIVATE_KEY_PATH" -pubout -out "$PUBLIC_KEY_PATH"

echo "密钥对生成完毕！"
echo "公钥: $PUBLIC_KEY_PATH"
echo "私钥: $PRIVATE_KEY_PATH"
echo ""
echo "请将公钥复制到 gateway/src/main/resources/keys/public.pem"
echo "请妥善保管私钥，不要将其提交到版本控制系统！"
