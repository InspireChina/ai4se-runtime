# RFC-0011 · Knowledge SPI

- Status: **Accepted-Draft**
- Related: ADR-0012, Architecture 18

## SPI

```
KnowledgePackDescriptor { id, version, scope: ORG|PROJECT }

KnowledgeItem {
  id, packId, kind, title, tags[], status,
  projectScope?, bodyRef, updatedAt
}

KnowledgeQuery { scopes[], text?, kinds[], tags[], modules[], limit }

KnowledgeHit { itemId, score, snippet, citeMeta }

KnowledgeEnginePort {
  search(query): List<KnowledgeHit>
  get(itemId): KnowledgeItem
  propose(draft): itemId
  transition(itemId, from, to, actor): void
}
```

## Status transitions

`DRAFT → ACTIVE → DEPRECATED`；允许 `DRAFT → REJECTED`。
