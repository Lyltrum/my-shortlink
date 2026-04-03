# ==================================================================
# JWT 密钥对生成说明
# ==================================================================
#
# 一键生成（在项目根目录执行）：
#   cd admin
#   bash generate-jwt-keys.sh
#
# 或直接在 IDE 中运行：
#   com.lu.shortlink.admin.util.JwtKeyGenerator.main()
#
# 生成后文件位置：
#   admin/src/main/resources/keys/private.pem  （私钥，妥善保管！）
#   admin/src/main/resources/keys/public.pem   （公钥）
#
# 部署步骤：
# 1. 将 admin/src/main/resources/keys/public.pem
#    复制到 gateway/src/main/resources/keys/public.pem
# 2. 切勿将 private.pem 提交到 Git！
# ==================================================================
