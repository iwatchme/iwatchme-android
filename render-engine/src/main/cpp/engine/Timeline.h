#pragma once

#include <cstdint>
#include <string>
#include <vector>

// 时间线上的一个片段。
struct Clip {
    std::string sourcePath;
    int64_t inPoint = 0;     // 全局时间线起始位置 (us)
    int64_t outPoint = 0;    // 全局时间线结束位置 (us)
    int64_t trimIn = 0;      // 源文件裁剪起点 (us)
    int64_t trimOut = 0;     // 源文件裁剪终点 (us)

    int64_t effectiveDuration() const { return outPoint - inPoint; }
    int64_t sourceDuration() const { return trimOut - trimIn; }
};

// resolve() 的返回值。
struct ClipLookup {
    int clipIndex = -1;              // 片段索引，-1 表示超出时间线
    int64_t sourcePositionUs = -1;   // 源文件内的位置
};

// 单轨时间线：管理一组顺序拼接的 Clip。
class Timeline {
public:
    Timeline() = default;

    // 设置片段列表，自动计算 inPoint/outPoint 使其无缝衔接。
    void setClips(std::vector<Clip> clips);

    // 追加片段到末尾。
    void appendClip(const std::string& sourcePath, int64_t trimIn, int64_t trimOut);

    // 全局位置 → 片段索引 + 源文件位置。
    // 如果 globalPosUs >= totalDuration，返回 {-1, -1}。
    ClipLookup resolve(int64_t globalPosUs) const;

    int64_t durationUs() const { return totalDurationUs_; }
    int clipCount() const { return (int)clips_.size(); }
    const Clip& clipAt(int index) const { return clips_[index]; }
    bool isEmpty() const { return clips_.empty(); }

private:
    void recalculateTimeline();

    std::vector<Clip> clips_;
    int64_t totalDurationUs_ = 0;
};
