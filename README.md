<h1 align="center">E-commerce Backend</h1>

<p align="center">
  <strong>An event-driven Spring Boot API for the storefront</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 4.1" />
  <img src="https://img.shields.io/badge/Spring%20Security-JWT-6DB33F?logo=springsecurity&logoColor=white" alt="Spring Security and JWT" />
  <img src="https://img.shields.io/badge/Apache%20Kafka-Events-231F20?logo=apachekafka&logoColor=white" alt="Apache Kafka" />
  <img src="https://img.shields.io/badge/MySQL-Persistence-4479A1?logo=mysql&logoColor=white" alt="MySQL" />
</p>

---

## Introduction

The E-commerce Backend owns customer accounts, saved addresses, the product catalog, inventory, and orders. Shoppers can sign in, recover their accounts, browse products, and track orders. Administrators manage products, stock, and order status through role-protected APIs.

When a shopper checks out, the backend prices the order from the catalog and deducts stock. It saves the pending order and its outgoing event in the same MySQL transaction using a transactional outbox. Payment outcomes arrive through Kafka and drive order confirmation or cancellation. A Spring event listener publishes the corresponding notification after the order transaction commits.

> **Checkout recovery verified in Razorpay Test Mode.** With the browser's payment verification request blocked, Razorpay delivered a capture webhook and the order reached `CONFIRMED`.
