// ITouchInjectorService.aidl
package com.panda.touch;

/**
 * Interface giao tiếp liên tiến trình (IPC) giữa Normal App Layer (UID thông thường)
 * và Root Service Layer (UID 0 - Root Daemon).
 *
 * Chịu trách nhiệm khởi tạo node /dev/input/eventX, cấu hình hệ tọa độ hiển thị,
 * và bơm các sự kiện chạm đa điểm (Multi-touch Type B) trực tiếp vào Linux Kernel.
 */
interface ITouchInjectorService {

    /**
     * Tự động quét /dev/input/event* và mở node màn hình cảm ứng (Touchscreen).
     * @return true nếu tìm thấy và mở thành công với quyền đọc/ghi O_RDWR.
     */
    boolean initTouchDevice();

    /**
     * Đường dẫn node thiết bị đang kết nối (ví dụ: "/dev/input/event2").
     */
    String getDevicePath();

    /**
     * Tên thiết bị phần cứng được báo cáo từ kernel ioctl (EVIOCGNAME).
     */
    String getDeviceName();

    /**
     * Giới hạn tọa độ phần cứng trục X (Min - Max).
     */
    int getHwMinX();
    int getHwMaxX();

    /**
     * Giới hạn tọa độ phần cứng trục Y (Min - Max).
     */
    int getHwMinY();
    int getHwMaxY();

    /**
     * Tổng số lượng Slot đa điểm mà phần cứng hỗ trợ (thường là 10).
     */
    int getMaxSlots();

    /**
     * Cập nhật thông số màn hình và hướng xoay hiện tại từ WindowManager / Display.
     * @param screenWidth Chiều rộng hiển thị (pixels).
     * @param screenHeight Chiều cao hiển thị (pixels).
     * @param displayRotation Surface.ROTATION_0, ROTATION_90, ROTATION_180, hoặc ROTATION_270.
     */
    void updateDisplayMetrics(int screenWidth, int screenHeight, int displayRotation);

    /**
     * Bơm sự kiện nhấn ngón tay (Touch Down) tại tọa độ màn hình.
     * @param slot Chỉ mục slot ảo (Quy ước: Slot 5 đến Slot 9).
     * @param screenX Tọa độ X trên màn hình hiển thị (pixel).
     * @param screenY Tọa độ Y trên màn hình hiển thị (pixel).
     */
    boolean injectTouchDown(int slot, float screenX, float screenY);

    /**
     * Bơm sự kiện di chuyển ngón tay (Touch Move) tại tọa độ màn hình.
     * @param slot Chỉ mục slot ảo (Quy ước: Slot 5 đến Slot 9).
     * @param screenX Tọa độ X mới trên màn hình (pixel).
     * @param screenY Tọa độ Y mới trên màn hình (pixel).
     */
    boolean injectTouchMove(int slot, float screenX, float screenY);

    /**
     * Bơm sự kiện nhấc ngón tay (Touch Up) giải phóng slot.
     * @param slot Chỉ mục slot ảo (Quy ước: Slot 5 đến Slot 9).
     */
    boolean injectTouchUp(int slot);

    /**
     * Thực hiện chạm nhanh (Tap) độc lập trong background thread của daemon.
     */
    boolean injectTap(int slot, float screenX, float screenY, int durationMs);

    /**
     * Thực hiện vuốt (Swipe) mượt mà nội suy nhiều bước.
     */
    boolean injectSwipe(int slot, float startX, float startY, float endX, float endY, int durationMs, int steps);

    /**
     * Hủy kích hoạt và giải phóng toàn bộ các slot ảo (Slot 5-9) để tránh kẹt cảm ứng.
     */
    void releaseAllVirtualSlots();

    /**
     * Đóng file descriptor và giải phóng tài nguyên native.
     */
    void closeDevice();
}
