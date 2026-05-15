#include "TextRenderer.h"

#include <ft2build.h>
#include FT_FREETYPE_H

#include <algorithm>
#include <cstring>

#include "common/log.h"

namespace {

constexpr int kShadowOffset = 2;
constexpr int kPadding = 6;

struct GlyphLayout {
    uint32_t codepoint;
    int penX;
    int penY;
    int bmpLeft;
    int bmpTop;
    int bmpW;
    int bmpH;
    int advanceX;
    std::vector<uint8_t> bmp;
};

}  // namespace

TextRenderer::~TextRenderer() {
    release();
}

bool TextRenderer::init(const std::string& fontPath, int fontSizePx) {
    release();
    if (FT_Init_FreeType(&ftLibrary_) != 0) {
        LOGE("TextRenderer: FT_Init_FreeType failed");
        return false;
    }
    if (FT_New_Face(ftLibrary_, fontPath.c_str(), 0, &ftFace_) != 0) {
        LOGE("TextRenderer: FT_New_Face failed for %s", fontPath.c_str());
        FT_Done_FreeType(ftLibrary_);
        ftLibrary_ = nullptr;
        return false;
    }
    if (FT_Set_Pixel_Sizes(ftFace_, 0, fontSizePx) != 0) {
        LOGE("TextRenderer: FT_Set_Pixel_Sizes %d failed", fontSizePx);
        FT_Done_Face(ftFace_);
        FT_Done_FreeType(ftLibrary_);
        ftFace_ = nullptr;
        ftLibrary_ = nullptr;
        return false;
    }
    fontSizePx_ = fontSizePx;
    LOGI("TextRenderer: init ok font=%s size=%d", fontPath.c_str(), fontSizePx);
    return true;
}

void TextRenderer::release() {
    if (ftFace_) {
        FT_Done_Face(ftFace_);
        ftFace_ = nullptr;
    }
    if (ftLibrary_) {
        FT_Done_FreeType(ftLibrary_);
        ftLibrary_ = nullptr;
    }
    fontSizePx_ = 0;
}

std::vector<uint32_t> TextRenderer::decodeUtf8(const std::string& utf8) {
    std::vector<uint32_t> out;
    out.reserve(utf8.size());
    size_t i = 0;
    while (i < utf8.size()) {
        uint8_t b = static_cast<uint8_t>(utf8[i]);
        uint32_t cp = 0;
        int extra = 0;
        if ((b & 0x80) == 0) {
            cp = b;
        } else if ((b & 0xE0) == 0xC0) {
            cp = b & 0x1F;
            extra = 1;
        } else if ((b & 0xF0) == 0xE0) {
            cp = b & 0x0F;
            extra = 2;
        } else if ((b & 0xF8) == 0xF0) {
            cp = b & 0x07;
            extra = 3;
        } else {
            ++i;
            continue;
        }
        ++i;
        for (int k = 0; k < extra && i < utf8.size(); ++k, ++i) {
            cp = (cp << 6) | (static_cast<uint8_t>(utf8[i]) & 0x3F);
        }
        out.push_back(cp);
    }
    return out;
}

bool TextRenderer::isCjk(uint32_t cp) {
    return (cp >= 0x4E00 && cp <= 0x9FFF) ||
           (cp >= 0x3000 && cp <= 0x30FF) ||
           (cp >= 0xFF00 && cp <= 0xFFEF) ||
           (cp >= 0x3400 && cp <= 0x4DBF);
}

bool TextRenderer::renderText(const std::string& utf8Text, int maxWidth, TextBitmap& out) {
    out.pixels.clear();
    out.width = 0;
    out.height = 0;
    if (!ftFace_) return false;

    auto codepoints = decodeUtf8(utf8Text);
    if (codepoints.empty()) return false;

    const int ascender = static_cast<int>(ftFace_->size->metrics.ascender >> 6);
    const int descender = static_cast<int>(-(ftFace_->size->metrics.descender >> 6));
    const int lineHeight = static_cast<int>(ftFace_->size->metrics.height >> 6);

    // Layout pass — collect glyphs grouped into visual lines.
    std::vector<std::vector<GlyphLayout>> lines;
    lines.emplace_back();
    int penX = 0;
    int lastBreakablePos = -1;  // index in current line where we can wrap
    int lastBreakablePenX = 0;

    auto flushLine = [&]() {
        lines.emplace_back();
        penX = 0;
        lastBreakablePos = -1;
        lastBreakablePenX = 0;
    };

    for (size_t i = 0; i < codepoints.size(); ++i) {
        uint32_t cp = codepoints[i];
        if (cp == '\n') {
            flushLine();
            continue;
        }
        if (FT_Load_Char(ftFace_, cp, FT_LOAD_RENDER) != 0) continue;

        FT_GlyphSlot g = ftFace_->glyph;
        GlyphLayout gl{};
        gl.codepoint = cp;
        gl.bmpLeft = g->bitmap_left;
        gl.bmpTop = g->bitmap_top;
        gl.bmpW = static_cast<int>(g->bitmap.width);
        gl.bmpH = static_cast<int>(g->bitmap.rows);
        gl.advanceX = static_cast<int>(g->advance.x >> 6);
        if (gl.bmpW > 0 && gl.bmpH > 0) {
            gl.bmp.assign(g->bitmap.buffer,
                          g->bitmap.buffer + gl.bmpW * gl.bmpH);
        }
        gl.penX = penX;
        gl.penY = 0;

        int next = penX + gl.advanceX;
        if (maxWidth > 0 && next > maxWidth && !lines.back().empty()) {
            if (isCjk(cp) || lastBreakablePos < 0) {
                // Wrap before current glyph
                flushLine();
                gl.penX = 0;
                next = gl.advanceX;
            } else {
                // Wrap at last space
                auto& cur = lines.back();
                std::vector<GlyphLayout> carry(cur.begin() + lastBreakablePos + 1, cur.end());
                cur.resize(lastBreakablePos);  // drop the space too
                flushLine();
                for (auto& c : carry) {
                    c.penX = penX;
                    penX += c.advanceX;
                    lines.back().push_back(c);
                }
                gl.penX = penX;
                next = penX + gl.advanceX;
            }
        }

        lines.back().push_back(gl);
        penX = next;
        if (cp == ' ' || isCjk(cp)) {
            lastBreakablePos = static_cast<int>(lines.back().size()) - 1;
            lastBreakablePenX = penX;
        }
    }

    // Compute final canvas size.
    int maxLineWidth = 0;
    for (auto& line : lines) {
        if (line.empty()) continue;
        const auto& last = line.back();
        int lineWidth = last.penX + last.advanceX;
        maxLineWidth = std::max(maxLineWidth, lineWidth);
    }
    if (maxLineWidth <= 0) return false;

    int canvasW = maxLineWidth + kPadding * 2 + kShadowOffset;
    int canvasH = static_cast<int>(lines.size()) * lineHeight + kPadding * 2 + kShadowOffset;

    out.width = canvasW;
    out.height = canvasH;
    out.pixels.assign(static_cast<size_t>(canvasW) * canvasH * 4, 0);

    auto blit = [&](const GlyphLayout& gl, int baseX, int baseY,
                    uint8_t r, uint8_t g, uint8_t b) {
        if (gl.bmp.empty()) return;
        int dstX0 = baseX + gl.bmpLeft;
        int dstY0 = baseY - gl.bmpTop;
        for (int y = 0; y < gl.bmpH; ++y) {
            int dy = dstY0 + y;
            if (dy < 0 || dy >= canvasH) continue;
            for (int x = 0; x < gl.bmpW; ++x) {
                int dx = dstX0 + x;
                if (dx < 0 || dx >= canvasW) continue;
                uint8_t a = gl.bmp[y * gl.bmpW + x];
                if (a == 0) continue;
                uint8_t* p = &out.pixels[(dy * canvasW + dx) * 4];
                // src-over blend
                float sa = a / 255.0f;
                float ia = 1.0f - sa;
                p[0] = static_cast<uint8_t>(r * sa + p[0] * ia);
                p[1] = static_cast<uint8_t>(g * sa + p[1] * ia);
                p[2] = static_cast<uint8_t>(b * sa + p[2] * ia);
                uint8_t pa = p[3];
                p[3] = static_cast<uint8_t>(a + pa * ia);
            }
        }
    };

    // Render: shadow pass (black, offset), then white pass.
    for (size_t li = 0; li < lines.size(); ++li) {
        int lineWidth = 0;
        if (!lines[li].empty()) {
            const auto& last = lines[li].back();
            lineWidth = last.penX + last.advanceX;
        }
        int xOffset = kPadding + (maxLineWidth - lineWidth) / 2;
        int baseY = kPadding + static_cast<int>(li) * lineHeight + ascender;
        for (const auto& gl : lines[li]) {
            blit(gl, xOffset + gl.penX + kShadowOffset, baseY + kShadowOffset, 0, 0, 0);
        }
        for (const auto& gl : lines[li]) {
            blit(gl, xOffset + gl.penX, baseY, 255, 255, 255);
        }
    }

    (void)descender;
    return true;
}

void TextRenderer::uploadToTexture(const TextBitmap& bitmap, GLuint& texId,
                                   int& texW, int& texH) {
    if (bitmap.pixels.empty() || bitmap.width <= 0 || bitmap.height <= 0) return;
    if (texId == 0) {
        glGenTextures(1, &texId);
    }
    glBindTexture(GL_TEXTURE_2D, texId);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, bitmap.width, bitmap.height, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, bitmap.pixels.data());
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_2D, 0);
    texW = bitmap.width;
    texH = bitmap.height;
}
