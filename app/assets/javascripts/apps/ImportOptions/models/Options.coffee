define [ 'backbone' ], (Backbone) ->
  Backbone.Model.extend
    defaults:
      split_documents: false
      supplied_stop_words: ''
      lang: ''

    initialize: (attributes, options) ->
      throw 'Must set options.defaultLanguageCode' if !options.defaultLanguageCode
      throw 'Must set options.supportedLanguages' if !options.supportedLanguages

      @supportedLanguages = options.supportedLanguages
      @set('lang', options.defaultLanguageCode)