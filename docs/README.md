# Documentation

This directory contains design documents and templates for the OpenSearch search-relevance plugin development.

## Contents

### Design Templates

- **[DESIGN_TEMPLATE.md](DESIGN_TEMPLATE.md)** - Template for technical design documents
  - Use this template when proposing new features or significant changes
  - Tailored specifically for the search-relevance plugin context
  - Includes OpenSearch-specific considerations and requirements

## Usage Guidelines

### When to Use the Design Template

Use the design template for:
- New features or significant functionality changes
- API modifications or additions
- Performance or architectural improvements
- Cross-component integrations
- Changes affecting backward compatibility

### Design Review Process

1. **Create Design Document**: Copy the template and fill in relevant sections
2. **Initial Review**: Share with the team for technical feedback
3. **Iteration**: Update based on review comments
4. **Approval**: Get maintainer approval before implementation
5. **Implementation**: Reference the design during development
6. **Update**: Keep the design document updated as implementation evolves

### Best Practices

- **Remove Unused Sections**: Delete template sections not relevant to your design
- **Link to Issues**: Reference related GitHub issues and discussions
- **Include Diagrams**: Use visual aids to clarify complex architectures
- **Consider Alternatives**: Document alternative approaches and trade-offs
- **Plan for Testing**: Define comprehensive testing strategies
- **Think Long-term**: Consider future extensibility and maintenance

## Contributing

For questions about using these templates or suggestions for improvements, please:
1. Open a GitHub issue with the `documentation` label
2. Follow the [Contributing Guidelines](../CONTRIBUTING.md)
3. Reference the [Developer Guide](../DEVELOPER_GUIDE.md) for setup instructions

## Example Issues for Design Template Usage

These GitHub issues from the search-relevance repository would benefit from using the design template:

1. **[Issue #126: LLM-as-a-judge for search quality evaluation](https://github.com/opensearch-project/search-relevance/issues/126)**
   - **Why it's a good example**: Integrates external LLM services with significant security implications
   - **Key design aspects**: Data privacy, API security, threat modeling for external service integration
   - **Template sections to focus on**: Security Considerations (especially threat analysis for data sent to LLMs), API Design, Testing Strategy

2. **[Issue #159: Enhanced task scheduling for experiment creation](https://github.com/opensearch-project/search-relevance/issues/159)**
   - **Why it's a good example**: This RFC involves major architectural changes to the task scheduling system
   - **Key design aspects**: API changes, performance considerations, backward compatibility, and security for task management
   - **Template sections to focus on**: Solution Design, Technical Specifications, Performance and Benchmarking

These examples demonstrate how the template helps structure complex feature proposals with proper security analysis and technical specifications.

## Related Resources

- [OpenSearch Plugin Development](https://opensearch.org/docs/latest/developers/plugins/)
- [OpenSearch RFC Process](https://github.com/opensearch-project/OpenSearch/blob/main/DEVELOPER_GUIDE.md#submitting-changes)
- [Search Relevance Plugin Architecture](../README.md)
