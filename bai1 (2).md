# BÁO CÁO: KHẮC PHỤC GIÁ FLASH SALE KHÔNG ĐỒNG NHẤT BẰNG REDIS

**Họ và tên:** [Điền họ và tên]  
**Mã sinh viên:** [Điền mã sinh viên]  
**Công nghệ:** Java 17, Spring Boot, Spring Cache, Redis, MySQL, Gradle

## 1. Nguyên nhân mất nhất quán (yêu cầu a)

Hệ thống Flash Sale có 10 instance; `localPriceCache` là `HashMap` thuộc bộ nhớ
của **mỗi instance**, không phải dữ liệu dùng chung. Instance A đọc giá từ DB rồi
lưu vào cache A; instance B có cache B hoàn toàn độc lập. Khi B cập nhật giá,
`updateProductPrice()` chỉ cập nhật DB và cache B, không có sự kiện hay cơ chế
vô hiệu hóa cache A, C, …, J. Vì nhánh cache hit không truy vấn DB, A tiếp tục
trả giá cũ dù DB đã đổi. `HashMap` còn không bảo đảm an toàn khi nhiều thread
truy cập đồng thời; thay bằng `ConcurrentHashMap` chỉ cải thiện an toàn luồng
**trong cùng instance**, không đồng bộ được 10 instance. Không có TTL khiến giá
cũ có thể tồn tại đến khi instance khởi động lại hoặc được thay đổi thủ công.

**Test case P001:**

| Thời điểm | Hành động | DB P001 | Cache A | Cache B | Kết quả |
|---|---|---:|---:|---:|---|
| T0 | Khởi tạo; các cache trống | 100.000đ | Trống | Trống | Chưa có sai lệch |
| T1 | A đọc P001, cache miss → đọc DB | 100.000đ | 100.000đ | Trống | A trả 100.000đ |
| T2 | B đổi giá P001 thành 80.000đ | 80.000đ | 100.000đ | 80.000đ | B trả 80.000đ |
| T3 | Hai khách đi vào A và B | 80.000đ | 100.000đ | 80.000đ | A trả 100.000đ; B trả 80.000đ |

Sai lệch xuất hiện vì cập nhật ở B không xóa giá cũ trong cache A. Đây là lỗi
nhất quán dữ liệu giữa các instance, không chỉ là vấn đề hiệu năng.

## 2. Giải pháp Redis và mã nguồn (yêu cầu b)

Dùng **Redis làm distributed cache** để mọi instance đọc cùng vùng
`productPrices`. DB MySQL là nguồn dữ liệu chuẩn. `@Cacheable` lấy giá từ Redis;
nếu cache miss thì truy vấn MySQL và đưa kết quả vào Redis. `@CacheEvict` xóa
đúng key khi sửa giá thành công; lần đọc kế tiếp trên bất kỳ instance nào đều
đọc giá mới từ DB rồi cache lại. Bật `@EnableCaching` để annotation được xử lý
bởi Spring Cache AOP Proxy.

Các tệp chính trong mã nguồn nộp kèm:

- `config/RedisCacheConfig.java`: RedisCacheManager, TTL 30 giây,
  JSON serializer, không cache null, đồng bộ thao tác cache sau DB commit.
- `service/ProductPriceService.java`: `@Cacheable` và `@CacheEvict`, kiểm tra
  tham số và gọi repository.
- `config/LoggingCacheErrorHandler.java`: fallback khi thao tác Redis lỗi.
- `repository/ProductPriceRepository.java`: truy vấn/cập nhật giá MySQL.

Đoạn annotation trọng tâm:

```java
@Cacheable(cacheNames = "productPrices", key = "#p0",
    condition = "#p0 != null && !#p0.isBlank()", unless = "#result == null")
public Integer getProductPrice(String productId) {
    validateProductId(productId);
    return productRepository.findPriceById(productId);
}

@Transactional
@CacheEvict(cacheNames = "productPrices", key = "#p0",
    condition = "#p0 != null && !#p0.isBlank()")
public void updateProductPrice(String productId, Integer newPrice) {
    validateProductId(productId);
    if (newPrice == null || newPrice < 0) {
        throw new IllegalArgumentException("newPrice phải là số nguyên >= 0");
    }
    productRepository.updatePrice(productId, newPrice);
}
```

Cache Manager gọi `.transactionAware()` để hoãn thao tác evict tới sau khi
transaction MySQL commit thành công. Sử dụng key mặc định dạng
`productPrices::P001`; chỉ xóa entry sản phẩm được cập nhật, không dùng
`allEntries = true` làm mất cache của toàn bộ sản phẩm.

## 3. Ngoại lệ và giới hạn của giải pháp (yêu cầu c)

**Redis down:** cấu hình timeout kết nối/đọc 300 ms và đăng ký
`CacheErrorHandler`. `handleCacheGetError()` ghi cảnh báo, không ném lại lỗi
nên Spring thực thi service và truy vấn MySQL; `handleCachePutError()` cũng
bỏ qua lỗi ghi cache. Khi update, DB tiếp tục được cập nhật nếu còn hoạt động;
lỗi evict được ghi ERROR để xử lý. Ưu điểm là duy trì API, nhược điểm là DB
chịu tải đột biến. Với 2 triệu người dùng, cần giám sát, giới hạn lưu lượng
và bảo vệ DB bằng backpressure/rate limiting. Không trả giá cũ từ local cache
trong fallback vì có thể tái tạo chính lỗi đã phát hiện.

**`productId` null/rỗng:** `condition` bỏ qua thao tác cache khi ID không hợp
lệ; `validateProductId()` ném `IllegalArgumentException` trước khi truy vấn
DB (HTTP 400 qua `@RestControllerAdvice`). Chỉ dùng `condition` là chưa đủ
vì method vẫn có thể thực thi; phải kiểm tra cả trong business method.
`newPrice` cũng phải khác null và không âm. `disableCachingNullValues()`
tránh lưu kết quả null; sản phẩm không tồn tại được trả HTTP 404.

**Giới hạn nhất quán:** shared Redis và evict khắc phục test tuần tự T0–T3,
nhưng không tạo giao dịch nguyên tử giữa MySQL và Redis. Nếu evict thất bại,
entry cũ có thể xuất hiện lại khi Redis phục hồi; TTL 30 giây chỉ giới hạn
thời gian sai lệch, **không bảo đảm nhất quán tức thời**. Trong production,
cần cơ chế retry/đối soát invalidation đáng tin cậy (ví dụ outbox + worker),
giám sát lỗi cache, và xác minh giá trực tiếp từ DB/nguồn có version lúc
chốt thanh toán. Cũng cần xử lý race giữa cache miss và cập nhật đồng thời,
chẳng hạn dùng giá có version hoặc kiểm soát đồng thời theo sản phẩm.

## 4. Kết quả kiểm thử đề xuất

Khởi chạy MySQL, Redis và hai instance chung hạ tầng. Đọc P001 qua A và B
(ban đầu 100.000đ), PATCH P001 qua B thành 80.000đ rồi đọc qua A: trong
luồng tuần tự, A và B đều trả 80.000đ. Tắt Redis, đọc giá vẫn thành công
từ MySQL và log cảnh báo; thử ID `null`, `""`, `"  "` trên unit test phải ném
`IllegalArgumentException` mà không chạm DB. Xem chi tiết lệnh và test trong
`README.md` cùng `ProductPriceServiceTest.java`.

**Kết luận:** Redis tập trung giải quyết phân mảnh cache giữa 10 instance;
eviction, validation và fallback làm luồng đọc/cập nhật ổn định hơn. Để áp
dụng Flash Sale thật, phải bổ sung kiểm soát cạnh tranh, phục hồi eviction
lỗi và kiểm chứng giá ở bước thanh toán.
