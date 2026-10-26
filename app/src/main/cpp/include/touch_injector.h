#ifndef PANDA_TOUCH_INJECTOR_H
#define PANDA_TOUCH_INJECTOR_H

#include <string>
#include <vector>
#include <mutex>
#include <cstdint>
#include <linux/input.h>

namespace panda {

// Định nghĩa dải slot dành riêng cho Virtual Touch (tránh xung đột với ngón tay thật 0-4)
constexpr int VIRTUAL_SLOT_MIN = 5;
constexpr int VIRTUAL_SLOT_MAX = 9;
constexpr int MAX_SUPPORTED_SLOTS = 16;

struct TouchDeviceBounds {
    int32_t min_x{0};
    int32_t max_x{0};
    int32_t min_y{0};
    int32_t max_y{0};
    int32_t max_slots{10};
    bool has_pressure{false};
    bool has_touch_major{false};
    bool has_btn_touch{false};
};

struct DisplayMetrics {
    int32_t width{0};
    int32_t height{0};
    int32_t rotation{0}; // 0 = ROTATION_0, 1 = ROTATION_90, 2 = ROTATION_180, 3 = ROTATION_270
};

class TouchInjector {
public:
    static TouchInjector& getInstance();

    // Khởi tạo và tự động tìm node cảm ứng
    bool autoDetectAndOpen();
    bool openDevice(const std::string& device_path);
    void closeDevice();

    // Cập nhật thông số màn hình
    void updateDisplayMetrics(int32_t width, int32_t height, int32_t rotation);

    // Chuyển đổi tọa độ pixel trên màn hình thành tọa độ phần cứng digitizer
    bool screenToHardware(float screen_x, float screen_y, int32_t& hw_x, int32_t& hw_y) const;

    // Các hàm bơm sự kiện đa điểm (Multi-touch Protocol Type B)
    bool injectTouchDown(int slot, float screen_x, float screen_y);
    bool injectTouchMove(int slot, float screen_x, float screen_y);
    bool injectTouchUp(int slot);

    // Giải phóng toàn bộ các slot ảo khi dừng/tạm dừng game
    void releaseAllVirtualSlots();

    // Truy vấn thông tin thiết bị
    bool isOpened() const { return fd_ >= 0; }
    std::string getDevicePath() const { return device_path_; }
    std::string getDeviceName() const { return device_name_; }
    const TouchDeviceBounds& getBounds() const { return bounds_; }

private:
    TouchInjector();
    ~TouchInjector();
    TouchInjector(const TouchInjector&) = delete;
    TouchInjector& operator=(const TouchInjector&) = delete;

    // Kiểm tra xem fd có phải là touchscreen hợp lệ không
    bool validateTouchDevice(int fd, TouchDeviceBounds& out_bounds, std::string& out_name);

    // Gửi mảng input_event xuống kernel qua write()
    bool writeEvents(const struct input_event* events, size_t count);

    int fd_{-1};
    std::string device_path_;
    std::string device_name_;
    TouchDeviceBounds bounds_;
    DisplayMetrics display_;

    // Trạng thái của từng slot
    bool slot_active_[MAX_SUPPORTED_SLOTS]{false};
    int32_t slot_tracking_id_[MAX_SUPPORTED_SLOTS]{-1};
    int32_t last_hw_x_[MAX_SUPPORTED_SLOTS]{0};
    int32_t last_hw_y_[MAX_SUPPORTED_SLOTS]{0};
    int32_t next_tracking_id_{1000};
    int active_virtual_touches_{0};

    mutable std::mutex mutex_;
};

} // namespace panda

#endif // PANDA_TOUCH_INJECTOR_H
