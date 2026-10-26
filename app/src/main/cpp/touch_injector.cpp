#include "include/touch_injector.h"

#include <dirent.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <linux/input.h>
#include <android/log.h>
#include <cstring>
#include <algorithm>

#define LOG_TAG "PandaTouchEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace panda {

// Helper kiểm tra bitmask trong mảng byte
static inline bool test_bit(int bit, const uint8_t* array) {
    return (array[bit / 8] & (1 << (bit % 8))) != 0;
}

TouchInjector& TouchInjector::getInstance() {
    static TouchInjector instance;
    return instance;
}

TouchInjector::TouchInjector() {
    for (int i = 0; i < MAX_SUPPORTED_SLOTS; ++i) {
        slot_active_[i] = false;
        slot_tracking_id_[i] = -1;
        last_hw_x_[i] = 0;
        last_hw_y_[i] = 0;
    }
}

TouchInjector::~TouchInjector() {
    closeDevice();
}

bool TouchInjector::autoDetectAndOpen() {
    std::lock_guard<std::mutex> lock(mutex_);

    if (fd_ >= 0) {
        LOGI("Device already opened: %s", device_path_.c_str());
        return true;
    }

    const char* input_dir = "/dev/input";
    DIR* dir = opendir(input_dir);
    if (!dir) {
        LOGE("Failed to open directory %s: %s", input_dir, strerror(errno));
        return false;
    }

    struct dirent* entry = nullptr;
    std::vector<std::string> candidates;

    while ((entry = readdir(dir)) != nullptr) {
        if (strncmp(entry->d_name, "event", 5) == 0) {
            candidates.push_back(std::string(input_dir) + "/" + entry->d_name);
        }
    }
    closedir(dir);

    // Sắp xếp theo thứ tự tự nhiên (event0, event1, ...)
    std::sort(candidates.begin(), candidates.end(), [](const std::string& a, const std::string& b) {
        int na = std::atoi(a.c_str() + 16);
        int nb = std::atoi(b.c_str() + 16);
        return na < nb;
    });

    LOGI("Scanning %zu input event nodes for Multi-touch Screen...", candidates.size());

    std::string best_device;
    TouchDeviceBounds best_bounds;
    std::string best_name;
    bool found = false;

    for (const auto& path : candidates) {
        int test_fd = open(path.c_str(), O_RDWR | O_NONBLOCK);
        if (test_fd < 0) {
            // Không mở được (có thể do quyền hạn hoặc SELinux nếu không có root)
            LOGD("Cannot open %s (errno: %d - %s)", path.c_str(), errno, strerror(errno));
            continue;
        }

        TouchDeviceBounds b;
        std::string name;
        if (validateTouchDevice(test_fd, b, name)) {
            LOGI("Found Touchscreen device: %s (%s) [X: %d-%d, Y: %d-%d, Slots: %d]",
                 path.c_str(), name.c_str(), b.min_x, b.max_x, b.min_y, b.max_y, b.max_slots);

            best_device = path;
            best_bounds = b;
            best_name = name;
            found = true;
            close(test_fd);
            break; // Tìm thấy thiết bị đạt chuẩn đầu tiên
        }
        close(test_fd);
    }

    if (!found) {
        LOGE("Could not find any suitable Multi-touch screen device in /dev/input!");
        return false;
    }

    // Mở node được chọn với quyền ghi
    fd_ = open(best_device.c_str(), O_RDWR | O_NONBLOCK);
    if (fd_ < 0) {
        LOGE("Failed to open confirmed device %s: %s", best_device.c_str(), strerror(errno));
        return false;
    }

    device_path_ = best_device;
    bounds_ = best_bounds;
    device_name_ = best_name;

    LOGI("Successfully initialized touch injector on %s (%s)", device_path_.c_str(), device_name_.c_str());
    return true;
}

bool TouchInjector::validateTouchDevice(int fd, TouchDeviceBounds& out_bounds, std::string& out_name) {
    uint8_t ev_bits[NBITS(EV_MAX)] = {0};
    uint8_t abs_bits[NBITS(ABS_MAX)] = {0};
    uint8_t prop_bits[NBITS(INPUT_PROP_MAX)] = {0};
    uint8_t key_bits[NBITS(KEY_MAX)] = {0};

    // 1. Kiểm tra EV_ABS
    if (ioctl(fd, EVIOCGBIT(0, sizeof(ev_bits)), ev_bits) < 0) return false;
    if (!test_bit(EV_ABS, ev_bits)) return false;

    // 2. Kiểm tra các trục ABS bắt buộc của Multi-touch Type B
    if (ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(abs_bits)), abs_bits) < 0) return false;

    if (!test_bit(ABS_MT_SLOT, abs_bits) ||
        !test_bit(ABS_MT_POSITION_X, abs_bits) ||
        !test_bit(ABS_MT_POSITION_Y, abs_bits) ||
        !test_bit(ABS_MT_TRACKING_ID, abs_bits)) {
        return false; // Không hỗ trợ Multi-touch Type B
    }

    // 3. Kiểm tra Properties (INPUT_PROP_DIRECT)
    bool is_direct = false;
    if (ioctl(fd, EVIOCGPROP(sizeof(prop_bits)), prop_bits) >= 0) {
        if (test_bit(INPUT_PROP_DIRECT, prop_bits)) {
            is_direct = true;
        }
    }

    // 4. Lấy biên phần cứng X và Y
    struct input_absinfo abs_x{}, abs_y{}, abs_slot{};
    if (ioctl(fd, EVIOCGABS(ABS_MT_POSITION_X), &abs_x) < 0) return false;
    if (ioctl(fd, EVIOCGABS(ABS_MT_POSITION_Y), &abs_y) < 0) return false;

    out_bounds.min_x = abs_x.minimum;
    out_bounds.max_x = abs_x.maximum;
    out_bounds.min_y = abs_y.minimum;
    out_bounds.max_y = abs_y.maximum;

    // Số lượng slot hỗ trợ
    if (ioctl(fd, EVIOCGABS(ABS_MT_SLOT), &abs_slot) >= 0) {
        out_bounds.max_slots = abs_slot.maximum + 1;
    } else {
        out_bounds.max_slots = 10;
    }

    // 5. Kiểm tra tính năng mở rộng: Pressure, Touch Major, BTN_TOUCH
    out_bounds.has_pressure = test_bit(ABS_MT_PRESSURE, abs_bits);
    out_bounds.has_touch_major = test_bit(ABS_MT_TOUCH_MAJOR, abs_bits);

    if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(key_bits)), key_bits) >= 0) {
        out_bounds.has_btn_touch = test_bit(BTN_TOUCH, key_bits);
    }

    // 6. Lấy tên thiết bị
    char name_buf[256] = {0};
    if (ioctl(fd, EVIOCGNAME(sizeof(name_buf) - 1), name_buf) >= 0) {
        out_name = name_buf;
    } else {
        out_name = "Unknown Touchscreen";
    }

    return true;
}

bool TouchInjector::openDevice(const std::string& device_path) {
    std::lock_guard<std::mutex> lock(mutex_);
    closeDevice();

    int fd = open(device_path.c_str(), O_RDWR | O_NONBLOCK);
    if (fd < 0) {
        LOGE("Cannot open %s: %s", device_path.c_str(), strerror(errno));
        return false;
    }

    TouchDeviceBounds b;
    std::string name;
    if (!validateTouchDevice(fd, b, name)) {
        LOGE("Device %s is not a valid Multi-touch Type B screen", device_path.c_str());
        close(fd);
        return false;
    }

    fd_ = fd;
    device_path_ = device_path;
    bounds_ = b;
    device_name_ = name;
    LOGI("Opened specified touch device: %s (%s)", device_path_.c_str(), device_name_.c_str());
    return true;
}

void TouchInjector::closeDevice() {
    if (fd_ >= 0) {
        releaseAllVirtualSlots();
        close(fd_);
        fd_ = -1;
        LOGI("Touch device closed.");
    }
}

void TouchInjector::updateDisplayMetrics(int32_t width, int32_t height, int32_t rotation) {
    std::lock_guard<std::mutex> lock(mutex_);
    display_.width = width;
    display_.height = height;
    display_.rotation = rotation;
    LOGI("Display metrics updated: %dx%d, Rotation: %d", width, height, rotation);
}

/**
 * Thuật toán quy đổi hệ tọa độ:
 * Màn hình cảm ứng vật lý (Hardware Digitizer) luôn cố định theo trục Portrait:
 * - Trục vật lý X: từ min_x (0) đến max_x (VD: 1079)
 * - Trục vật lý Y: từ min_y (0) đến max_y (VD: 2399)
 *
 * Khi chơi game, thiết bị thường xoay ngang (Landscape - ROTATION_90 hoặc ROTATION_270).
 * Cần chuyển đổi tọa độ điểm chạm trên giao diện (screen_x, screen_y) sang (hw_x, hw_y) chính xác.
 */
bool TouchInjector::screenToHardware(float screen_x, float screen_y, int32_t& hw_x, int32_t& hw_y) const {
    if (display_.width <= 0 || display_.height <= 0) {
        LOGE("Invalid display metrics: %dx%d", display_.width, display_.height);
        return false;
    }

    // Chuẩn hóa tọa độ màn hình về dải [0.0, 1.0]
    float norm_x = std::clamp(screen_x / static_cast<float>(display_.width), 0.0f, 1.0f);
    float norm_y = std::clamp(screen_y / static_cast<float>(display_.height), 0.0f, 1.0f);

    float raw_hw_x = 0.0f;
    float raw_hw_y = 0.0f;

    switch (display_.rotation) {
        case 0: // ROTATION_0: Portrait mặc định
            raw_hw_x = bounds_.min_x + norm_x * (bounds_.max_x - bounds_.min_x);
            raw_hw_y = bounds_.min_y + norm_y * (bounds_.max_y - bounds_.min_y);
            break;

        case 1: // ROTATION_90: Landscape (Xoay ngược chiều kim đồng hồ 90 độ, đỉnh máy ở bên TRÁI)
            // Screen X (0 -> width) tương ứng với trục vật lý Y (min_y -> max_y)
            // Screen Y (0 -> height) tương ứng với trục vật lý X (max_x -> min_x)
            raw_hw_y = bounds_.min_y + norm_x * (bounds_.max_y - bounds_.min_y);
            raw_hw_x = bounds_.max_x - norm_y * (bounds_.max_x - bounds_.min_x);
            break;

        case 2: // ROTATION_180: Reverse Portrait
            raw_hw_x = bounds_.max_x - norm_x * (bounds_.max_x - bounds_.min_x);
            raw_hw_y = bounds_.max_y - norm_y * (bounds_.max_y - bounds_.min_y);
            break;

        case 3: // ROTATION_270: Reverse Landscape (Xoay theo chiều kim đồng hồ 90 độ, đỉnh máy ở bên PHẢI)
            // Screen X (0 -> width) tương ứng với trục vật lý Y (max_y -> min_y)
            // Screen Y (0 -> height) tương ứng với trục vật lý X (min_x -> max_x)
            raw_hw_y = bounds_.max_y - norm_x * (bounds_.max_y - bounds_.min_y);
            raw_hw_x = bounds_.min_x + norm_y * (bounds_.max_x - bounds_.min_x);
            break;

        default:
            LOGE("Unsupported rotation: %d", display_.rotation);
            return false;
    }

    hw_x = std::clamp(static_cast<int32_t>(raw_hw_x + 0.5f), bounds_.min_x, bounds_.max_x);
    hw_y = std::clamp(static_cast<int32_t>(raw_hw_y + 0.5f), bounds_.min_y, bounds_.max_y);
    return true;
}

bool TouchInjector::writeEvents(const struct input_event* events, size_t count) {
    if (fd_ < 0) return false;
    ssize_t expected = sizeof(struct input_event) * count;
    ssize_t written = write(fd_, events, expected);
    if (written != expected) {
        LOGE("Failed to write input events: %s (written %zd / %zd)", strerror(errno), written, expected);
        return false;
    }
    return true;
}

/**
 * Bơm sự kiện DOWN cho một Slot độc lập (Slot 5-9)
 */
bool TouchInjector::injectTouchDown(int slot, float screen_x, float screen_y) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (fd_ < 0) return false;

    if (slot < VIRTUAL_SLOT_MIN || slot > VIRTUAL_SLOT_MAX) {
        LOGE("Invalid virtual slot %d! Must be in [%d, %d]", slot, VIRTUAL_SLOT_MIN, VIRTUAL_SLOT_MAX);
        return false;
    }

    int32_t hw_x = 0, hw_y = 0;
    if (!screenToHardware(screen_x, screen_y, hw_x, hw_y)) {
        return false;
    }

    // Nếu slot đã active trước đó, xem như hành động MOVE để tránh lỗi protocol
    if (slot_active_[slot]) {
        return injectTouchMove(slot, screen_x, screen_y);
    }

    // Sinh Tracking ID độc lập, tăng dần
    int32_t tracking_id = next_tracking_id_++;
    if (next_tracking_id_ > 65000) {
        next_tracking_id_ = 1000;
    }

    slot_active_[slot] = true;
    slot_tracking_id_[slot] = tracking_id;
    last_hw_x_[slot] = hw_x;
    last_hw_y_[slot] = hw_y;

    struct input_event evs[10];
    int count = 0;

    // 1. Chọn Slot
    evs[count++] = { .type = EV_ABS, .code = ABS_MT_SLOT, .value = slot };
    // 2. Gán Tracking ID
    evs[count++] = { .type = EV_ABS, .code = ABS_MT_TRACKING_ID, .value = tracking_id };
    // 3. Tọa độ X và Y
    evs[count++] = { .type = EV_ABS, .code = ABS_MT_POSITION_X, .value = hw_x };
    evs[count++] = { .type = EV_ABS, .code = ABS_MT_POSITION_Y, .value = hw_y };

    // 4. Các thông số phụ trợ nếu driver yêu cầu (Pressure, Touch Major)
    if (bounds_.has_pressure) {
        evs[count++] = { .type = EV_ABS, .code = ABS_MT_PRESSURE, .value = 50 };
    }
    if (bounds_.has_touch_major) {
        evs[count++] = { .type = EV_ABS, .code = ABS_MT_TOUCH_MAJOR, .value = 8 };
    }

    // 5. BTN_TOUCH: Nếu đây là ngón tay ảo đầu tiên chạm vào màn hình
    if (active_virtual_touches_ == 0 && bounds_.has_btn_touch) {
        evs[count++] = { .type = EV_KEY, .code = BTN_TOUCH, .value = 1 };
    }
    active_virtual_touches_++;

    // 6. SYN_REPORT: Commit toàn bộ gói tin sự kiện xuống Kernel
    evs[count++] = { .type = EV_SYN, .code = SYN_REPORT, .value = 0 };

    return writeEvents(evs, count);
}

/**
 * Bơm sự kiện MOVE cho Slot
 */
bool TouchInjector::injectTouchMove(int slot, float screen_x, float screen_y) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (fd_ < 0) return false;

    if (slot < VIRTUAL_SLOT_MIN || slot > VIRTUAL_SLOT_MAX) {
        return false;
    }

    if (!slot_active_[slot]) {
        // Slot chưa Down thì không thể Move
        return false;
    }

    int32_t hw_x = 0, hw_y = 0;
    if (!screenToHardware(screen_x, screen_y, hw_x, hw_y)) {
        return false;
    }

    // Bỏ qua nếu tọa độ không thay đổi để tiết kiệm CPU và tránh flood kernel queue
    if (last_hw_x_[slot] == hw_x && last_hw_y_[slot] == hw_y) {
        return true;
    }

    last_hw_x_[slot] = hw_x;
    last_hw_y_[slot] = hw_y;

    struct input_event evs[6];
    int count = 0;

    // 1. Chọn Slot
    evs[count++] = { .type = EV_ABS, .code = ABS_MT_SLOT, .value = slot };
    // 2. Cập nhật tọa độ X, Y
    evs[count++] = { .type = EV_ABS, .code = ABS_MT_POSITION_X, .value = hw_x };
    evs[count++] = { .type = EV_ABS, .code = ABS_MT_POSITION_Y, .value = hw_y };
    // 3. SYN_REPORT
    evs[count++] = { .type = EV_SYN, .code = SYN_REPORT, .value = 0 };

    return writeEvents(evs, count);
}

/**
 * Bơm sự kiện UP để nhả Slot
 */
bool TouchInjector::injectTouchUp(int slot) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (fd_ < 0) return false;

    if (slot < VIRTUAL_SLOT_MIN || slot > VIRTUAL_SLOT_MAX) {
        return false;
    }

    if (!slot_active_[slot]) {
        return true; // Đã up rồi
    }

    slot_active_[slot] = false;
    slot_tracking_id_[slot] = -1;

    struct input_event evs[6];
    int count = 0;

    // 1. Chọn Slot
    evs[count++] = { .type = EV_ABS, .code = ABS_MT_SLOT, .value = slot };
    // 2. Giải phóng slot: Đặt ABS_MT_TRACKING_ID về -1
    evs[count++] = { .type = EV_ABS, .code = ABS_MT_TRACKING_ID, .value = -1 };

    if (active_virtual_touches_ > 0) {
        active_virtual_touches_--;
    }

    // 3. BTN_TOUCH: Nếu không còn ngón tay ảo nào chạm vào màn hình
    if (active_virtual_touches_ == 0 && bounds_.has_btn_touch) {
        evs[count++] = { .type = EV_KEY, .code = BTN_TOUCH, .value = 0 };
    }

    // 4. SYN_REPORT
    evs[count++] = { .type = EV_SYN, .code = SYN_REPORT, .value = 0 };

    return writeEvents(evs, count);
}

/**
 * Giải phóng khẩn cấp toàn bộ các slot ảo (Slot 5-9)
 */
void TouchInjector::releaseAllVirtualSlots() {
    for (int s = VIRTUAL_SLOT_MIN; s <= VIRTUAL_SLOT_MAX; ++s) {
        if (slot_active_[s]) {
            injectTouchUp(s);
        }
    }
    active_virtual_touches_ = 0;
}

} // namespace panda
