# Flash Sale – Redis Distributed Cache (Spring Boot + Gradle)

Bài tập minh hoạ lỗi giá không đồng nhất do 10 instance dùng local `HashMap`.
Bản sửa dùng Redis tập trung, `@Cacheable` khi đọc, `@CacheEvict` khi đổi giá,
`CacheErrorHandler` khi Redis down và validation để loại `productId` không hợp lệ.
Báo cáo phân tích và test case T0–T3: [BAO_CAO.md](BAO_CAO.md).

## Công nghệ

Java 17, Spring Boot 3.5.6, Gradle, Spring JDBC, MySQL 8.4, Redis 7.
Tệp `docker-compose.yml` chỉ khởi chạy **hạ tầng** MySQL và Redis.
Đây là cấu hình phục vụ học tập; tài khoản/mật khẩu mẫu không dành cho production.

## 1. Chạy hạ tầng

Cần cài Docker Desktop và bật Docker Engine:

```powershell
docker compose up -d
docker compose ps
```

MySQL: `localhost:3307`, DB `flash_sale`, username `flashsale`, password `flashsale_dev`.
Redis: `localhost:6379`. Đợi MySQL có trạng thái healthy.

## 2. Chạy 2 instance trong IntelliJ

1. `File > Open` thư mục dự án, chọn Gradle JVM là Java 17, đồng bộ Gradle.
2. Chạy `FlashSaleApplication` bằng Run Configuration thứ nhất, đặt
   `Program arguments`: `--server.port=8080`.
3. Nhân đôi Run Configuration, bật **Allow multiple instances** nếu IDE yêu cầu,
   đặt `Program arguments`: `--server.port=8081`, rồi chạy.
4. Cả hai instance dùng **cùng MySQL và Redis**, không chạy DB riêng từng instance.

Nếu có Gradle cài sẵn, có thể chạy ở hai terminal riêng:

```powershell
gradle bootRun --args='--server.port=8080'
gradle bootRun --args='--server.port=8081'
```

File `schema.sql` tạo bảng, `data.sql` chỉ thêm P001=100000 khi chưa tồn tại.

## 3. Kiểm thử bằng PowerShell

```powershell
# Lần đọc đầu: DB 100000 và cache trên Redis.
Invoke-RestMethod http://localhost:8080/api/products/P001/price
Invoke-RestMethod http://localhost:8081/api/products/P001/price

# Đổi giá thông qua instance B: ghi MySQL rồi evict key chung ở Redis.
Invoke-RestMethod -Method Patch `
  -Uri http://localhost:8081/api/products/P001/price `
  -ContentType 'application/json' `
  -Body '{"newPrice":80000}'

# Instance A đọc lại cùng một giá 80000.
Invoke-RestMethod http://localhost:8080/api/products/P001/price
docker compose exec redis redis-cli GET productPrices::P001

# Kiểm tra Redis down: ứng dụng vẫn đọc DB.
docker compose stop redis
Invoke-RestMethod http://localhost:8080/api/products/P001/price
docker compose start redis

# productId trống: thử trực tiếp ProductPriceService trong unit test.
# URL %20 có thể bị web server từ chối trước khi vào controller.
```

Chạy unit tests bằng IntelliJ (nhấn Run trên `ProductPriceServiceTest`) hoặc `gradle test`.
Nếu Redis down trong lúc **evict**, Redis cũ có thể chứa giá lỗi thời sau khi
phục hồi; ứng dụng ghi log ERROR, TTL giới hạn thời gian sống còn 30 giây.
Production cần cơ chế retry/reconciliation và đối soát DB lúc chốt đơn.

## 4. Đẩy lên GitHub

Tạo một repository **trống** trên tài khoản GitHub, sau đó mở PowerShell trong
thư mục `flash-sale-redis` và chạy:

```powershell
git init
git add .
git commit -m "Fix flash sale price inconsistency with Redis cache"
git branch -M main
git remote add origin <URL_REPOSITORY_CUA_BAN>
git push -u origin main
```

Nếu Git báo `remote origin already exists`, dùng
`git remote set-url origin <URL_REPOSITORY_CUA_BAN>` trước `git push`.
Không commit `.env`, mật khẩu thật hoặc token truy cập.

## Lưu ý thiết kế

- `@EnableCaching` bắt buộc để Spring Cache AOP có hiệu lực.
- `@Cacheable` có `condition` chống tạo cache key rác; validation trong method
  đảm bảo fail-fast và trả HTTP 400 cho input không hợp lệ.
- `@CacheEvict` chỉ xóa entry của `P001`, không xóa cả cache.
- `.transactionAware()` tránh evict trước khi transaction MySQL commit.
- Shared Redis + evict giải quyết bài toán tuần tự T0–T3 nhưng **không phải
  đảm bảo linearizability** khi cập nhật và cache miss diễn ra đồng thời.
- Demo endpoint PATCH chưa có authentication; production phải bảo vệ endpoint
  và lấy giá thanh toán từ DB/nguồn giá có kiểm soát phiên bản.
