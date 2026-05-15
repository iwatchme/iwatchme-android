#pragma once

#include <GLES3/gl3.h>

#include <cstdint>
#include <string>
#include <vector>

struct FT_LibraryRec_;
struct FT_FaceRec_;
typedef FT_LibraryRec_* FT_Library;
typedef FT_FaceRec_* FT_Face;

struct TextBitmap {
    std::vector<uint8_t> pixels;
    int width = 0;
    int height = 0;
};

class TextRenderer {
public:
    TextRenderer() = default;
    ~TextRenderer();

    bool init(const std::string& fontPath, int fontSizePx);
    void release();
    bool isReady() const { return ftFace_ != nullptr; }

    int fontSizePx() const { return fontSizePx_; }

    bool renderText(const std::string& utf8Text, int maxWidth, TextBitmap& out);
    void uploadToTexture(const TextBitmap& bitmap, GLuint& texId, int& texW, int& texH);

private:
    static std::vector<uint32_t> decodeUtf8(const std::string& utf8);
    static bool isCjk(uint32_t cp);

    FT_Library ftLibrary_ = nullptr;
    FT_Face ftFace_ = nullptr;
    int fontSizePx_ = 0;
};
