# MyFamilyTV — Launcher Android TV siêu nhẹ cho gia đình (84.5 KB)

**MyFamilyTV** là Android TV / Google TV Launcher viết bằng **Java thuần** (không thư viện nặng), được thiết kế tối ưu riêng cho các dòng TV cấu hình thấp (RAM 2GB như Sony BRAVIA 4K UR3 chạy Android 10).

## ✨ Tính năng nổi bật

- **Siêu nhẹ:** File APK Release chỉ **~84.5 KB**, chiếm RAM nhẹ hơn ~4.4 lần so với Google TV Launcher gốc.
- **Màn hình chính tối giản & tươi sáng:**
  - Trình chiếu **Slideshow ảnh gia đình Full HD (`ARGB_8888`)**, tự xoay đúng chiều EXIF.
  - **Đồng hồ + Ngày dương & Âm lịch Việt Nam** (100% offline) + **Thời tiết** tự cập nhật.
  - **150 câu Ca dao – Tục ngữ, Nguyên tắc gia đình & Nguyên tắc sống tốt** tự xáo trộn ngẫu nhiên.
  - **Lịch trình hàng ngày** tự hiện thông báo to giữa màn hình kèm chuông báo và nhạc phù hợp.
  - Chỉ để **3 ứng dụng chính + Cài đặt**; bấm phím **Xuống (`↓`)** mở lưới 4 cột toàn bộ ứng dụng trên TV.
- **Chế độ Trẻ em chống khóc:**
  - Giới hạn phút xem/ngày tùy chỉnh.
  - Hết giờ chuyển sang màn hình *"Bạn Tivi buồn ngủ đang đi sạc pin 📺😴🌙"*.
  - **Câu đố kiến thức** chọn bằng phím số `1–4` trên remote (đúng thưởng `+5 phút`, tối đa 2 lần/ngày).
  - Mở khóa bằng **mã PIN động 4 số bí mật** (`HHMM` hoặc `DDMM`) qua bàn phím số thiết kế riêng cho TV.
- **Trang quản lý tích hợp trên điện thoại (`http://<IP_TV>:8080`):**
  - Không cần cài app phụ trên iPhone/Android: tải nhiều ảnh gia đình, quản lý nhạc MP3 theo 4 thể loại (*Thể dục sáng, Thiếu nhi, Nhạc Việt, Thư giãn*), gửi/hẹn giờ thông báo lên TV, kéo thả lịch trình, bật/tắt Chế độ Trẻ em từ xa.
- **Tự động tắt ban đêm (`22:30`) → Tự bật sáng (`06:30`):** Có hỗ trợ bật thủ công ngay nếu bấm bất kỳ phím nào trên remote.

## 🛠️ Cách build từ mã nguồn

Yêu cầu trên Windows:
- **Java JDK 11** (`C:\Program Files\Java\jdk-11.0.4`)
- **Android SDK** (`platforms\android-28` & `build-tools\28.0.3`)

Chạy script build:
```powershell
powershell -ExecutionPolicy Bypass -File .\build-apk.ps1
```

File APK đã ký sẽ được tạo tại `FamilyCleanTV-release.apk`.

## 📺 Cài đặt lên Android TV qua ADB

```bash
adb connect <IP_CUA_TV>:5555
adb install -r FamilyCleanTV-release.apk
```

*(Tùy chọn) Đặt làm Launcher mặc định:*
```bash
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell pm disable-user --user 0 com.google.android.tvrecommendations
adb shell cmd package set-home-activity vn.family.cleanlauncher/.MainActivity
```

*(Khôi phục Launcher Google gốc bất cứ lúc nào):*
```bash
adb shell pm enable com.google.android.tvlauncher
```
