package com.company;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

public class CreditCardPaymentTracker {

    static class CreditCard {
        String name;
        double amountDue;
        int dueDayOfMonth; // day of month payment is due (e.g. 15 = 15th of each month)

        CreditCard(String name, double amountDue, int dueDayOfMonth) {
            this.name = name;
            this.amountDue = amountDue;
            this.dueDayOfMonth = dueDayOfMonth;
        }

        LocalDate nextMonthDueDate(YearMonth nextMonth) {
            int lastDay = nextMonth.lengthOfMonth();
            int day = Math.min(dueDayOfMonth, lastDay);
            return nextMonth.atDay(day);
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        List<CreditCard> cards = new ArrayList<>();

        System.out.println("=== Credit Card Payment Tracker ===");
        System.out.println("Enter your credit cards (type 'done' when finished)\n");

        while (true) {
            System.out.print("Card name (or 'done'): ");
            String name = scanner.nextLine().trim();
            if (name.equalsIgnoreCase("done")) break;
            if (name.isEmpty()) continue;

            double amount = 0;
            while (true) {
                System.out.print("Amount due ($): ");
                String amountStr = scanner.nextLine().trim();
                try {
                    amount = Double.parseDouble(amountStr);
                    if (amount < 0) throw new NumberFormatException();
                    break;
                } catch (NumberFormatException e) {
                    System.out.println("  Please enter a valid positive number.");
                }
            }

            int dueDay = 0;
            while (true) {
                System.out.print("Due day of month (1-31): ");
                String dayStr = scanner.nextLine().trim();
                try {
                    dueDay = Integer.parseInt(dayStr);
                    if (dueDay < 1 || dueDay > 31) throw new NumberFormatException();
                    break;
                } catch (NumberFormatException e) {
                    System.out.println("  Please enter a day between 1 and 31.");
                }
            }

            cards.add(new CreditCard(name, amount, dueDay));
            System.out.println("  Added: " + name + "\n");
        }

        if (cards.isEmpty()) {
            System.out.println("\nNo cards entered.");
            return;
        }

        // Determine next month
        LocalDate today = LocalDate.now();
        YearMonth nextMonth = YearMonth.from(today).plusMonths(1);
        Month month = nextMonth.getMonth();
        int year = nextMonth.getYear();

        // Sort by due date ascending
        cards.sort(Comparator.comparingInt(c -> c.dueDayOfMonth));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMMM d, yyyy");

        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.printf("║   Payments Due in %-39s║%n", month + " " + year);
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.printf("║  %-20s  %-15s  %-16s║%n", "Card", "Amount Due", "Due Date");
        System.out.println("╠══════════════════════════════════════════════════════════╣");

        double total = 0;
        for (CreditCard card : cards) {
            LocalDate dueDate = card.nextMonthDueDate(nextMonth);
            System.out.printf("║  %-20s  $%-14.2f  %-16s║%n",
                    card.name, card.amountDue, fmt.format(dueDate));
            total += card.amountDue;
        }

        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.printf("║  %-20s  $%-14.2f  %-16s║%n", "TOTAL", total, "");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        scanner.close();
    }
}
