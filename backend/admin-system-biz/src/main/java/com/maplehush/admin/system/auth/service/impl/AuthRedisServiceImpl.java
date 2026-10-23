package com.maplehush.admin.system.auth.service.impl;

import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.generator.RandomGenerator;

import com.maplehush.admin.common.exception.BusinessException;
import com.maplehush.admin.common.exception.ErrorCode;
import com.maplehush.admin.config.redis.RedisKeyBuilder;
import com.maplehush.admin.system.auth.service.IAuthRedisService;
import com.maplehush.admin.system.auth.support.AuthDigests;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 验证码、固定窗口限流和登录快照都使用带环境前缀的 Redis 键
 */
@Service
public class AuthRedisServiceImpl implements IAuthRedisService {
    /**
     * 验证码字符、画布大小和干扰强度的统一配置
     */
    private static final int CAPTCHA_WIDTH = 160;
    private static final int CAPTCHA_HEIGHT = 56;
    private static final int CAPTCHA_CODE_LENGTH = 4;
    private static final int CAPTCHA_INTERFERENCE_LINES = 12;
    private static final String CAPTCHA_CHARACTERS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final Color[] CAPTCHA_COLORS = {
        new Color(36, 113, 185),
        new Color(222, 145, 31),
        new Color(196, 76, 104),
        new Color(51, 143, 105),
        new Color(117, 99, 176)
    };
    private static final DefaultRedisScript<String> TAKE =
            new DefaultRedisScript<>(
                    "local v=redis.call('GET',KEYS[1]); if v then redis.call('DEL',KEYS[1]); end; return v",
                    String.class);
    private static final DefaultRedisScript<Long> LIMIT =
            new DefaultRedisScript<>(
                    "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return n",
                    Long.class);
    private final RedisTemplate<String, String> redis;
    private final RedisKeyBuilder keys;
    private final Duration snapshotTtl;

    public AuthRedisServiceImpl(
            @Qualifier("stringValueRedisTemplate") RedisTemplate<String, String> redis,
            RedisKeyBuilder keys,
            @Value("${maple-hush.auth.user-snapshot-ttl-seconds:600}") long snapshotTtlSeconds) {
        if (snapshotTtlSeconds < 1 || snapshotTtlSeconds > 86400) {
            throw new IllegalArgumentException("用户快照 TTL 须为 1 至 86400 秒");
        }
        this.redis = redis;
        this.keys = keys;
        this.snapshotTtl = Duration.ofSeconds(snapshotTtlSeconds);
    }

    @Override
    public Captcha issueCaptcha(String ip) {
        checkLimit("captcha_ip", AuthDigests.digest(ip), 10, 60);
        LineCaptcha image = new ColorfulOutlineCaptcha();
        String id = UUID.randomUUID().toString().replace("-", "");
        try {
            redis.opsForValue()
                    .set(
                            keys.key("captcha", id),
                            image.getCode().toUpperCase(Locale.ROOT),
                            Duration.ofMinutes(2));
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
        return new Captcha(id, Base64.getEncoder().encodeToString(image.getImageBytes()));
    }

    @Override
    public void checkLoginLimit(String ip, String username) {
        checkLimit("login_ip", AuthDigests.digest(ip), 20, 60);
        try {
            String count = redis.opsForValue().get(accountFailuresKey(username));
            if (count != null && Long.parseLong(count) >= 8) {
                throw new BusinessException(ErrorCode.RATE_LIMITED, "操作过于频繁，请稍后再试");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void recordCredentialFailure(String username) {
        checkLimit(
                "login_failure", AuthDigests.digest(username.toLowerCase(Locale.ROOT)), 8, 15 * 60);
    }

    @Override
    public void clearCredentialFailures(String username) {
        try {
            redis.delete(accountFailuresKey(username));
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private String accountFailuresKey(String username) {
        return keys.key("login_failure", AuthDigests.digest(username.toLowerCase(Locale.ROOT)));
    }

    @Override
    public void consumeCaptcha(String id, String answer) {
        if (id == null || !id.matches("[a-fA-F0-9]{32}") || answer == null) {
            throw new BusinessException(ErrorCode.CAPTCHA_INVALID, "验证码无效，请重新获取");
        }
        try {
            String expected = redis.execute(TAKE, List.of(keys.key("captcha", id)));
            if (expected == null
                    || !MessageDigest.isEqual(
                            expected.getBytes(StandardCharsets.UTF_8),
                            answer.trim()
                                    .toUpperCase(Locale.ROOT)
                                    .getBytes(StandardCharsets.UTF_8))) {
                throw new BusinessException(ErrorCode.CAPTCHA_INVALID, "验证码无效，请重新获取");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public String readSnapshot(long userId) {
        try {
            return redis.opsForValue().get(keys.key("user", Long.toString(userId)));
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void writeSnapshot(long userId, String json) {
        try {
            redis.opsForValue().set(keys.key("user", Long.toString(userId)), json, snapshotTtl);
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void clearSnapshot(long userId) {
        try {
            redis.delete(keys.key("user", Long.toString(userId)));
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    @Override
    public void checkPasswordChangeLimit(long userId) {
        checkLimit("password_change", Long.toString(userId), 5, 300);
    }

    private void checkLimit(String purpose, String id, int max, int windowSeconds) {
        try {
            Long count =
                    redis.execute(
                            LIMIT, List.of(keys.key(purpose, id)), Integer.toString(windowSeconds));
            if (count == null) {
                throw new IllegalStateException("Rate limit script returned null");
            }
            if (count > max) {
                throw new BusinessException(ErrorCode.RATE_LIMITED, "操作过于频繁，请稍后再试");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private static BusinessException unavailable(RuntimeException cause) {
        return new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "服务暂不可用，请稍后重试");
    }

    /**
     * 绘制彩色描边、轻微倾斜的字符验证码，降低线条对字符辨认的干扰。
     */
    private static final class ColorfulOutlineCaptcha extends LineCaptcha {
        private ColorfulOutlineCaptcha() {
            super(
                    CAPTCHA_WIDTH,
                    CAPTCHA_HEIGHT,
                    new RandomGenerator(CAPTCHA_CHARACTERS, CAPTCHA_CODE_LENGTH),
                    CAPTCHA_INTERFERENCE_LINES);
        }

        @Override
        public Image createImage(String code) {
            BufferedImage image =
                    new BufferedImage(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(
                        RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, CAPTCHA_WIDTH, CAPTCHA_HEIGHT);
                drawInterferenceLines(graphics);
                drawCharacters(graphics, code);
            } finally {
                graphics.dispose();
            }
            return image;
        }

        private void drawInterferenceLines(Graphics2D graphics) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            graphics.setStroke(
                    new BasicStroke(0.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int index = 0; index < CAPTCHA_INTERFERENCE_LINES; index++) {
                Color color = CAPTCHA_COLORS[index % CAPTCHA_COLORS.length];
                graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 48));
                graphics.drawLine(
                        random.nextInt(CAPTCHA_WIDTH),
                        random.nextInt(CAPTCHA_HEIGHT),
                        random.nextInt(CAPTCHA_WIDTH),
                        random.nextInt(CAPTCHA_HEIGHT));
            }
        }

        private void drawCharacters(Graphics2D graphics, String code) {
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, 37);
            FontRenderContext context = graphics.getFontRenderContext();
            int slotWidth = CAPTCHA_WIDTH / code.length();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int colorOffset = random.nextInt(CAPTCHA_COLORS.length);

            for (int index = 0; index < code.length(); index++) {
                char[] character = {code.charAt(index)};
                GlyphVector glyphVector = font.createGlyphVector(context, character);
                Shape glyph = glyphVector.getOutline();
                Rectangle2D bounds = glyph.getBounds2D();
                double x = index * slotWidth + (slotWidth - bounds.getWidth()) / 2 - bounds.getX();
                double y = (CAPTCHA_HEIGHT - bounds.getHeight()) / 2 - bounds.getY();
                double angle = random.nextDouble(-0.16, 0.16);
                double verticalOffset = random.nextDouble(-2.0, 2.0);
                AffineTransform originalTransform = graphics.getTransform();

                graphics.translate(x, y + verticalOffset);
                graphics.rotate(angle, bounds.getCenterX(), bounds.getCenterY());
                Color color = CAPTCHA_COLORS[(index + colorOffset) % CAPTCHA_COLORS.length];
                graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 28));
                graphics.fill(glyph);
                graphics.setStroke(
                        new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.setColor(color);
                graphics.draw(glyph);
                graphics.setTransform(originalTransform);
            }
        }
    }
}
