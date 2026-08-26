package com.aashir.ecommerce.service;

import com.aashir.ecommerce.event.OrderCancelledEvent;
import com.aashir.ecommerce.event.OrderCreatedEvent;
import com.aashir.ecommerce.event.OrderItemEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private static final Logger log =
            LoggerFactory.getLogger(EmailService.class);

    public void sendOrderCreatedEmail(OrderCreatedEvent event) {

       String subject ="Order Confirmed -" +event.orderNumber();
       String html = buildOrderEmail(
               event.customerName(),
               event.orderNumber(),
               event.status().name(),
               event.items(),
               event.totalAmount(),
               false
       );
        sendEmail(event.customerEmail(), subject, html);
    }

    public void sendOrderCancelledEmail(OrderCancelledEvent event) {

        String subject = "Order Cancelled - " + event.orderNumber();
        String html = buildOrderEmail(
                event.customerName(),
                event.orderNumber(),
                event.status().name(),
                event.items(),
                event.totalAmount(),
                true
        );

        sendEmail(event.customerEmail(), subject, html);
    }


    private void sendEmail(String sendemail, String subject, String html) {
        try{
            MimeMessage message  = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(message ,true,"UTF-8");
            helper.setTo(sendemail);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
            log.info(
                    "Email sent successfully. sendemail={}, subject={}",
                    sendemail,
                    subject
            );
        }catch (MessagingException | RuntimeException e){
            log.error(
                    "Failed to send email. sendemail={}, subject={}",
                    sendemail,
                    subject,
                    e
            );
        }
    }

       private String buildOrderEmail(String customerName,String orderNumber,String status,
                              java.util.List<OrderItemEvent> items,
                              java.math.BigDecimal totalAmount,
                               boolean cancelled

       ){
           StringBuilder itemRows = new StringBuilder();

           for (OrderItemEvent item : items) {

               itemRows.append("""
                    <tr>
                        <td style="padding:12px;border-bottom:1px solid #eee;">
                            %s
                        </td>
                        <td style="padding:12px;border-bottom:1px solid #eee;text-align:center;">
                            %d
                        </td>
                        <td style="padding:12px;border-bottom:1px solid #eee;text-align:right;">
                            ₹%s
                        </td>
                        <td style="padding:12px;border-bottom:1px solid #eee;text-align:right;">
                            ₹%s
                        </td>
                    </tr>
                    """.formatted(
                       item.productName(),
                       item.quantity(),
                       item.price(),
                       item.subtotal()
               ));
           }

           String heading = cancelled
                   ? "Your order has been cancelled"
                   : "Your order has been successfully placed!";

           String statusMessage = cancelled
                   ? "We're sorry to see your order cancelled."
                   : "Thank you for your purchase!";

           return """
                <!DOCTYPE html>
                <html>
                <body style="
                    margin:0;
                    padding:0;
                    background:#f5f5f5;
                    font-family:Arial,Helvetica,sans-serif;
                ">

                    <div style="
                        max-width:700px;
                        margin:30px auto;
                        background:#ffffff;
                        border-radius:8px;
                        overflow:hidden;
                        box-shadow:0 2px 8px rgba(0,0,0,0.08);
                    ">

                        <div style="
                            background:#111827;
                            color:white;
                            padding:25px;
                            text-align:center;
                        ">
                            <h1 style="margin:0;">
                                E-Commerce
                            </h1>
                        </div>

                        <div style="padding:30px;">

                            <h2>
                                Hi %s,
                            </h2>

                            <p>
                                %s
                            </p>

                            <p>
                                %s
                            </p>

                            <div style="
                                background:#f9fafb;
                                padding:15px;
                                border-radius:6px;
                                margin:20px 0;
                            ">
                                <strong>Order Number:</strong> %s<br>
                                <strong>Status:</strong> %s
                            </div>

                            <h3>Order Items</h3>

                            <table style="
                                width:100%%;
                                border-collapse:collapse;
                                margin-top:15px;
                            ">

                                <thead>
                                    <tr style="
                                        background:#f3f4f6;
                                    ">
                                        <th style="padding:12px;text-align:left;">
                                            Product
                                        </th>
                                        <th style="padding:12px;text-align:center;">
                                            Quantity
                                        </th>
                                        <th style="padding:12px;text-align:right;">
                                            Price
                                        </th>
                                        <th style="padding:12px;text-align:right;">
                                            Subtotal
                                        </th>
                                    </tr>
                                </thead>

                                <tbody>
                                    %s
                                </tbody>

                            </table>

                            <div style="
                                text-align:right;
                                margin-top:20px;
                                font-size:18px;
                            ">
                                <strong>
                                    Total: ₹%s
                                </strong>
                            </div>

                            <p style="
                                margin-top:30px;
                                color:#666;
                            ">
                                %s
                            </p>

                        </div>

                        <div style="
                            background:#f3f4f6;
                            padding:20px;
                            text-align:center;
                            color:#777;
                            font-size:13px;
                        ">
                            This is an automated email from E-Commerce.
                            Please do not reply to this email.
                        </div>

                    </div>

                </body>
                </html>
                """.formatted(
                   customerName,
                   heading,
                   statusMessage,
                   orderNumber,
                   status,
                   itemRows,
                   totalAmount,
                   cancelled
                           ? "If you did not request this cancellation, please contact support."
                           : "We appreciate your business and hope you enjoy your purchase!"
           );
       }

}