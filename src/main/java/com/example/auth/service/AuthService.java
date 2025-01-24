package com.example.auth.service;

import com.example.auth.entity.Otp;
import com.example.auth.entity.User;
import com.example.auth.repository.OtpRepository;
import com.example.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final JavaMailSender mailSender;

    private static final int OTP_EXPIRATION_MINUTES = 5;
    public String login(String email, String password) {
        // Tìm người dùng theo email
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found."));

        // Kiểm tra mật khẩu, nếu khớp thì login thành công
        if (!user.getPassword().equals(password)) {
            throw new RuntimeException("Invalid password.");
        }

        // Trả về thông báo khi đăng nhập thành công
        return "Login successful";
    }

    @Transactional
    public void registerUser(String email, String password) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists.");
        }
        User user = new User();
        user.setEmail(email);
        user.setPassword(password); // Store hashed password in a real application
        userRepository.save(user);

        // Xóa tất cả OTP cũ cho email
        otpRepository.deleteByEmail(email);

        String otpCode = generateOtp(email);
        sendOtpEmail(email, otpCode);
    }



    public void verifyOtp(String email, String otpCode) {
        // Kiểm tra OTP hợp lệ và chưa hết hạn
        Optional<Otp> optionalOtp = otpRepository.findByEmailAndOtpCode(email, otpCode);

        if (optionalOtp.isEmpty() || optionalOtp.get().getExpirationTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Invalid or expired OTP.");
        }

        // Kiểm tra người dùng
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found."));

        // Lưu người dùng vào cơ sở dữ liệu sau khi OTP hợp lệ
        user.setVerified(true);
        userRepository.save(user);

        // Xóa OTP sau khi đã xác thực
        otpRepository.delete(optionalOtp.get());
    }


    public void forgotPassword(String email) {
        if (userRepository.findByEmail(email).isEmpty()) {
            throw new RuntimeException("User not found.");
        }
        String otpCode = generateOtp(email);
        sendOtpEmail(email, otpCode);
    }

    @Transactional
    public void resetPassword(String email, String otpCode, String newPassword) {
        verifyOtp(email, otpCode);
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found."));
        user.setPassword(newPassword); // Store hashed password in a real application
        userRepository.save(user);
    }

    private String generateOtp(String email) {
        String otpCode = String.valueOf(100000 + new Random().nextInt(900000));
        Otp otp = otpRepository.findByEmail(email).orElse(new Otp());
        otp.setEmail(email);
        otp.setOtpCode(otpCode);
        otp.setExpirationTime(LocalDateTime.now().plusMinutes(OTP_EXPIRATION_MINUTES));
        otpRepository.save(otp);
        return otpCode;
    }

    private void sendOtpEmail(String email, String otpCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Your OTP Code");
        message.setText("Your OTP code is: " + otpCode);
        mailSender.send(message);
    }
}
