# OpenAI API Key Test Scripts

This directory contains several scripts to test if your OpenAI API key is working correctly. Choose the one that fits your environment.

## 🔑 Available Test Scripts

### 1. PowerShell Script (Windows)
**File:** `test-openai-api.ps1`

**Usage:**
```powershell
# Option 1: Set environment variable first
$env:OPENAI_API_KEY = "your-actual-api-key-here"
.\test-openai-api.ps1

# Option 2: Pass API key as parameter
.\test-openai-api.ps1 -ApiKey "your-actual-api-key-here"
```

### 2. Bash Script (Linux/macOS/WSL)
**File:** `test-openai-api.sh`

**Usage:**
```bash
# Make script executable
chmod +x test-openai-api.sh

# Set environment variable and run
export OPENAI_API_KEY="your-actual-api-key-here"
./test-openai-api.sh
```

### 3. Java Spring Boot Test
**File:** `src/test/java/com/deepai/mcpclient/test/OpenAIApiTest.java`

**Usage:**
```bash
# Set environment variable
export OPENAI_API_KEY="your-actual-api-key-here"

# Run the test
mvn test -Dtest=OpenAIApiTest
```

## 🚀 Quick Test

1. **Get your OpenAI API Key:**
   - Go to [OpenAI Platform](https://platform.openai.com/api-keys)
   - Create a new API key
   - Copy the key (starts with `sk-`)

2. **Choose your script and run it**

3. **Expected successful output:**
   ```
   ✅ API Connection Successful!
   
   📊 Response Details:
     Model: gpt-3.5-turbo
     Usage: 15 tokens
     Response: API test successful
   
   🎉 Your OpenAI API key is working correctly!
   ```

## 🔧 Troubleshooting

### Common Issues:

**401 Unauthorized:**
- Your API key is invalid or incorrect
- Check that you copied the full key correctly

**403 Forbidden:**
- Your API key doesn't have the required permissions
- Check your OpenAI account status

**429 Rate Limit:**
- You've exceeded your API usage limits
- Wait and try again, or check your OpenAI billing

**500 Server Error:**
- OpenAI is experiencing issues
- Check [OpenAI Status](https://status.openai.com/)

## 💡 Integration with Your Project

Once your API key is working, you can:

1. **Set it in your environment:**
   ```bash
   export OPENAI_API_KEY="your-api-key-here"
   ```

2. **Use it in Spring AI configuration:**
   ```yaml
   spring:
     ai:
       openai:
         api-key: ${OPENAI_API_KEY}
   ```

3. **Test with your MCP Client:**
   ```bash
   java -jar target/global-mcp-client-1.0.0-SNAPSHOT.jar shell
   ```

## 🔒 Security Notes

- **Never commit API keys to version control**
- **Use environment variables for production**
- **Rotate your keys regularly**
- **Monitor your API usage**

---

**Happy coding!** 🎉
