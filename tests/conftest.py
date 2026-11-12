"""Test configuration for the TALOS server suite.

The suite writes to the database through the same ``server.db`` module the
always-on API uses, so without this it writes into production: a plain
``pytest tests`` run was adding token rows to ``server/data/talos.db`` every
time.

``server.test_isolation`` has to run before ``server.db`` is imported, because
it works by pointing ``TALOS_DB_PATH`` at a throwaway copy before the module
reads it. A conftest is imported before any test module, which is the only hook
early enough. Importing it here rather than passing ``-p`` on the command line
means the isolation is the default and cannot be forgotten.
"""

import server.test_isolation  # noqa: F401  (imported for its side effects)