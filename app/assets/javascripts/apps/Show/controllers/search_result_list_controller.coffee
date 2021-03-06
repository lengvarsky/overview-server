define [
  '../models/DocumentListParams'
  '../views/InlineSearchResultList'
  'i18n'
], (DocumentListParams, InlineSearchResultListView, i18n) ->
  t = i18n.namespaced('views.Tree.show.SearchResultList')

  searchResultToTagName = (searchResultModel) ->
    t('tag_name', searchResultModel.get('query'))

  # SearchResult controller
  #
  # Arguments:
  #
  # * documentSet: a DocumentSet (for tagging, SearchResults, Tags)
  # * state: a State (for reading and manipulating the doclist)
  # * el: an HTMLElement (optional)
  #
  # Returned properties:
  #
  # * view: a Backbone.View
  (options) ->
    documentSet = options.documentSet
    searchResults = documentSet.searchResults
    tags = documentSet.tags
    state = options.state
    el = options.el

    view = new InlineSearchResultListView
      collection: searchResults
      canCreateTagFromSearchResult: (searchResult) ->
        searchResult && !tags.findWhere(name: searchResultToTagName(searchResult))?
      state: state
      el: el

    view.on 'search-result-clicked', (searchResult) ->
      state.resetDocumentListParams().bySearchResult(searchResult)

    view.on 'create-tag-clicked', (searchResult) ->
      tag = tags.create(name: searchResultToTagName(searchResult))
      params = state.get('documentListParams').reset.bySearchResult(searchResult)
      documentSet.tag(tag, params)

    view.on 'create-submitted', (query) ->
      searchResult = searchResults.create(query: query)
      searchResults.pollUntilStable()
      state.set(oneDocumentSelected: false) # https://www.pivotaltracker.com/story/show/65130854
      state.resetDocumentListParams().bySearchResult(searchResult)

    { view: view }
