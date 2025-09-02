# Contributing to Global MCP Client

We welcome contributions to the Global MCP Client! This document provides guidelines for contributing to the project.

## 🚀 Quick Start

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/global-mcp-client.git
   cd global-mcp-client
   ```
3. **Create a branch** for your feature or bug fix:
   ```bash
   git checkout -b feature/your-feature-name
   ```

## 🔨 Development Setup

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- Git

### Building the Project
```bash
# Install dependencies and build
mvn clean compile

# Run tests
mvn test

# Package the application
mvn clean package
```

### Running the Application
```bash
# Using Maven
mvn spring-boot:run

# Or using the JAR
java -jar target/global-mcp-client-1.0.0-SNAPSHOT.jar
```

## 📝 Making Changes

### Code Style
- Follow Java naming conventions
- Use clear, descriptive variable and method names
- Add Javadoc comments for public methods and classes
- Keep methods focused and concise
- Use Spring Boot patterns and conventions

### Testing
- Write unit tests for new functionality
- Ensure all tests pass: `mvn test`
- Add integration tests for complex features
- Maintain test coverage above 80%

### Commit Messages
Use clear, descriptive commit messages:
```
feat: add support for HTTP MCP servers
fix: resolve connection timeout issues
docs: update API documentation
test: add unit tests for server detection
```

## 🎯 Types of Contributions

### Bug Reports
When reporting bugs, please include:
- Clear description of the issue
- Steps to reproduce
- Expected vs actual behavior
- Environment details (Java version, OS, etc.)
- Relevant logs or error messages

### Feature Requests
For feature requests, please provide:
- Clear description of the proposed feature
- Use case or problem it solves
- Any implementation ideas
- Backwards compatibility considerations

### Pull Requests
Before submitting a PR:
1. **Test thoroughly** - ensure your changes work as expected
2. **Update documentation** - add/update docs for new features
3. **Add tests** - include unit/integration tests
4. **Check compatibility** - ensure backwards compatibility
5. **Follow code style** - maintain consistent formatting

## 🧪 Testing

### Running Tests
```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=McpClientServiceTest

# Integration tests
mvn integration-test
```

### Test Categories
- **Unit Tests**: Test individual components in isolation
- **Integration Tests**: Test complete workflows with MCP servers
- **Contract Tests**: Verify MCP protocol compliance

### Test Data
- Use test-specific MCP servers when possible
- Mock external dependencies in unit tests
- Clean up test data after tests complete

## 📚 Documentation

### Types of Documentation
- **API Documentation**: Update OpenAPI specs for new endpoints
- **User Documentation**: Update README, guides, and examples
- **Code Documentation**: Add Javadoc comments for public APIs
- **Configuration Documentation**: Document new configuration options

### Documentation Standards
- Use clear, concise language
- Include practical examples
- Keep documentation up-to-date with code changes
- Test all example code

## 🔍 Code Review Process

1. **Submit PR** with clear description and context
2. **Automated checks** must pass (builds, tests, linting)
3. **Peer review** by maintainers or contributors
4. **Address feedback** and make requested changes
5. **Final approval** and merge by maintainers

### Review Criteria
- Code quality and style
- Test coverage and quality
- Documentation completeness
- Backwards compatibility
- Performance considerations

## 🏷️ Release Process

### Version Numbering
We use [Semantic Versioning](https://semver.org/):
- **MAJOR**: Breaking changes
- **MINOR**: New features (backwards compatible)
- **PATCH**: Bug fixes (backwards compatible)

### Release Checklist
- [ ] All tests pass
- [ ] Documentation updated
- [ ] Version numbers updated
- [ ] Release notes prepared
- [ ] Security review completed

## 🤝 Community Guidelines

### Code of Conduct
- Be respectful and inclusive
- Welcome newcomers and help them learn
- Focus on constructive feedback
- Assume good intentions

### Getting Help
- **Issues**: Create GitHub issues for bugs or questions
- **Discussions**: Use GitHub Discussions for general questions
- **Documentation**: Check README and docs/ directory first

## 🎉 Recognition

Contributors are recognized through:
- GitHub contributor statistics
- Release notes acknowledgments
- Special recognition for significant contributions

## 📋 Checklist for Contributors

Before submitting a pull request, ensure:

- [ ] Code follows project style guidelines
- [ ] All tests pass locally
- [ ] New tests added for new functionality
- [ ] Documentation updated (if needed)
- [ ] Commit messages are clear and descriptive
- [ ] PR description explains the changes
- [ ] Breaking changes are clearly documented
- [ ] Security implications considered

## 🚀 Getting Started with Your First Contribution

1. **Look for "good first issue" labels** on GitHub
2. **Start small** - fix typos, improve documentation, or add tests
3. **Ask questions** - don't hesitate to ask for help or clarification
4. **Learn by doing** - start with small changes and gradually take on larger tasks

Thank you for contributing to Global MCP Client! 🙏
