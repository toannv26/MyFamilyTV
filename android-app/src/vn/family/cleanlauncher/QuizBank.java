package vn.family.cleanlauncher;

import java.util.Random;

public class QuizBank {
    public static class Question {
        public final String q;
        public final String[] opts;
        public final int ans;
        public Question(String q, String[] opts, int ans) {
            this.q = q;
            this.opts = opts;
            this.ans = ans;
        }
    }

    private static final Question[] BANK = new Question[] {
        new Question("Con vật nào sống dưới nước và biết phun cột nước trên lưng?",
                new String[]{"A. Cá voi", "B. Con gà", "C. Con mèo", "D. Con thỏ"}, 0),
        new Question("Phép tính nhanh: 7 + 5 bằng bao nhiêu?",
                new String[]{"A. 11", "B. 12", "C. 13", "D. 14"}, 1),
        new Question("Trong tiếng Anh, quả Táo đọc là gì?",
                new String[]{"A. Banana", "B. Orange", "C. Apple", "D. Mango"}, 2),
        new Question("Một tuần lễ có mấy ngày?",
                new String[]{"A. 5 ngày", "B. 6 ngày", "C. 7 ngày", "D. 8 ngày"}, 2),
        new Question("Khi nhận được quà từ người lớn, bé nên nói gì?",
                new String[]{"A. Im lặng", "B. Cháu cảm ơn ạ", "C. Bỏ đi chơi", "D. Không cần"}, 1),
        new Question("Phép tính nhanh: 15 - 6 bằng bao nhiêu?",
                new String[]{"A. 8", "B. 9", "C. 10", "D. 7"}, 1),
        new Question("Mặt trời mọc ở hướng nào vào buổi sáng?",
                new String[]{"A. Hướng Đông", "B. Hướng Tây", "C. Hướng Nam", "D. Hướng Bắc"}, 0),
        new Question("Trong tiếng Anh, màu Xanh dương là gì?",
                new String[]{"A. Red", "B. Yellow", "C. Blue", "D. Green"}, 2),
        new Question("Con vật nào có cái vòi rất dài?",
                new String[]{"A. Con hổ", "B. Con voi", "C. Con khỉ", "D. Con chó"}, 1),
        new Question("Phép tính nhanh: 4 × 3 bằng bao nhiêu?",
                new String[]{"A. 10", "B. 11", "C. 12", "D. 14"}, 2),
        new Question("Trước khi ăn cơm, bé cần làm gì để giữ vệ sinh?",
                new String[]{"A. Rửa tay sạch sẽ", "B. Xem tivi", "C. Chạy nhảy", "D. Đi ngủ"}, 0),
        new Question("Một năm có tất cả mấy tháng?",
                new String[]{"A. 10 tháng", "B. 11 tháng", "C. 12 tháng", "D. 13 tháng"}, 2)
    };

    private static final Random RNG = new Random();

    public static Question randomQuestion() {
        return BANK[RNG.nextInt(BANK.length)];
    }
}
