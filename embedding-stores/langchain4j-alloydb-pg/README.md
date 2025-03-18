# Google AlloyDB for PostgreSQL

The **AlloyDB for PostgreSQL for LangChain4J** package provides a first class experience for connecting to
AlloyDB instances from the LangChain ecosystem while providing the following benefits:

- **Simplified & Secure Connections**: easily and securely create shared connection pools to connect to Google Cloud databases utilizing IAM for authorization and database authentication without needing to manage SSL certificates, configure firewall rules, or enable authorized networks.
- **Improved performance & Simplified management**: use a single-table schema can lead to faster query execution, especially for large collections.
- **Improved metadata handling**: store metadata in columns instead of JSON, resulting in significant performance improvements.
- **Clear separation**: clearly separate table and extension creation, allowing for distinct permissions and streamlined workflows.
- **Better integration with AlloyDB**: built-in methods to take advantage of AlloyDB's advanced indexing and scalability capabilities.

Learn more about [AlloyDB for PostgreSQL](https://cloud.google.com/alloydb).

## Before you begin

In order to use this library, you first need to go through the following
steps:

1. [Select or create a Cloud Platform project.](https://console.cloud.google.com/project)
2. [Enable billing for your project.](https://cloud.google.com/billing/docs/how-to/modify-project#enable_billing_for_a_project)
3. [Enable the AlloyDB API.](https://console.cloud.google.com/flows/enableapi?apiid=alloydb.googleapis.com)
4. [Setup Authentication.](https://googleapis.dev/python/google-api-core/latest/auth.html)
5. [Create a database.](https://cloud.google.com/alloydb/docs/quickstart/create-and-connect)