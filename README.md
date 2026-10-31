# Commit Explainer 🤖✍️

Commit Explainer is a lightweight Spring Boot application that translates raw `git show` diffs into clear, beginner-friendly explanations using a local AI model. It is designed to act like a friendly programming professor, helping students and developers easily understand code changes without sacrificing privacy or spending money on cloud APIs.

---

## ✨ Key Features

- **100% Local & Private:** Powered by Ollama (`gemma2:2b`), ensuring your code never leaves your machine.
- **Zero-Token Cost:** No paid cloud APIs, no subscription fees, and no rate limits. Run it as many times as you want for free.
- **Beginner-Friendly AI Persona:** Formatted to explain code changes simply and educationally, avoiding overly complex technical jargon where possible.
- **Noise Filtering:** Automatically filters out irrelevant automated build files, wrappers (`mvnw`), and lockfiles to maximize context window efficiency for your actual code.
- **Secret Masking:** Automatically detects and redacts sensitive data (like passwords, tokens, and API keys) before sending the diff to the model.
- **Flexible Sources:** Supports both local repositories (`git show`) and remote GitHub commits via URL.

---

## 🛠️ Tech Stack

- **Backend:** Java 17+, Spring Boot
- **AI Engine:** Ollama (`gemma2:2b` local model)
- **Tooling:** Git CLI / GitHub integration

---

## 🚀 Getting Started

### Prerequisites

Make sure you have the following installed on your machine:
1. **Java JDK 21**
2. **Maven** (or use the project's wrapper)
3. **[Ollama](https://ollama.com/)** running locally.

### Setup Instructions

1. **Pull and run the local AI model:**
   Make sure Ollama is installed and running, then pull the `gemma2:2b` model:
   ```bash
   ollama run gemma2:2b
