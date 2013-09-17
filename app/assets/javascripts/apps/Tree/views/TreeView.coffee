define [
  'jquery'
  'underscore'
  '../models/observable'
  '../models/color_table'
  './DrawOperation'
  'jquery.mousewheel' # to catch the 'mousewheel' event properly
], ($, _, observable, ColorTable, DrawOperation) ->
  DEFAULT_OPTIONS = {
    lineStyles:
      # Just a hash
      normal: '#999999'
      highlighted: '#6ab9e9'

    nodeStyleRules: [
      # Similar to CSS, nodes have several classes.
      #
      # Here, similar to CSS, later rules take precedence. Each entry
      # must have a "type" (akin to HTML className). The special type
      # "" applies to all nodes.
      {
        type: ''
        fillStyle: '#ffffff'
        strokeStyle: '#666666'
        lineWidth: 2
      }
      {
        type: 'highlighted'
        strokeStyle: '#6ab9e9'
      }
      {
        type: 'hover'
        fillStyle: '#f4f4f4'
      }
      {
        type: 'selected'
        fillStyle: '#f4f4f4'
        lineWidth: 4
      }
      {
        type: 'leaf'
        lineWidth: 1
      }
    ]
    maxNodeBorderWidth: 4 # px
    nodeCornerRadius: 5 # px

    connector_line_width: 2.5 # px
    node_expand_width: 1.5 # px
    node_expand_click_radius: 12 # px
    node_expand_radius: 8 # px
    node_expand_glyph_radius: 4 # px
    mousewheel_zoom_factor: 1.2
  }

  HOVER_NODE_TEMPLATE = _.template("""
    <div class="inner">(<%- node.size.toLocaleString() %>) <%- node.description %></div>
  """)

  class TreeView
    observable(this)

    constructor: (@div, @cache, @tree, @focus, options={}) ->
      @options = _.extend({}, DEFAULT_OPTIONS, options)
      @state = @tree.state

      $div = $(@div)
      @canvas = $("<canvas width=\"#{$div.width()}\" height=\"#{$div.height()}\"></canvas>")[0]
      $div.append(@canvas)
      @$hover_node_description = $('<div class="hover-node-description" style="display:none;"></div>')
      $div.append(@$hover_node_description) # FIXME find a better place for this

      $zoom_buttons = $("""<div class="zoom-buttons">
          <button class="zoom-in">+</button>
          <button class="zoom-out">-</button>
        </div>""")
      $div.append($zoom_buttons)
      @zoomInButton = $zoom_buttons.find('.zoom-in')[0]
      @zoomOutButton = $zoom_buttons.find('.zoom-out')[0]

      this._attach()
      this.update()

    nodeid_above: (nodeid) ->
      @tree.id_tree.parent[nodeid]

    nodeid_below: (nodeid) ->
      try_nodeid = @tree.id_tree.children[nodeid]?[0]
      if @tree.on_demand_tree.nodes[try_nodeid]?
        try_nodeid
      else
        undefined

    # Returns the node to the left or right of the given node.
    #
    # If there is no sibling, this method will return undefined.
    _sibling: (nodeid, indexDiff) ->
      parentid = @tree.id_tree.parent[nodeid]
      if parentid
        siblings = @tree.id_tree.children[parentid]
        nodeIndex = siblings.indexOf(nodeid)
        siblingIndex = nodeIndex + indexDiff

        if 0 <= siblingIndex < siblings.length
          siblings[siblingIndex]
        else
          undefined
      else
        # root node has no siblings
        undefined

    nodeid_left: (nodeid) -> @_sibling(nodeid, -1)

    nodeid_right: (nodeid) -> @_sibling(nodeid, 1)

    _attach: () ->
      update = this._set_needs_update.bind(this)
      @tree.state.on('change:selection change:taglike', update)
      @tree.observe('needs-update', update)
      @focus.on('change', update)
      @cache.tag_store.observe('changed', update)
      $(window).on('resize.tree-view', update)

      @focus.on('change:zoom', this._refresh_zoom_button_status.bind(this))

      $(@zoomInButton).on 'click', (e) =>
        e.preventDefault()
        e.stopPropagation() # don't pan
        @_notify('zoom-pan', { zoom: @focus.get('zoom') * 0.5, pan: @focus.get('pan') }, { animate: true })
      $(@zoomOutButton).on 'click', (e) =>
        e.preventDefault()
        e.stopPropagation() # don't pan
        @_notify('zoom-pan', { zoom: @focus.get('zoom') * 2, pan: @focus.get('pan') }, { animate: true })

      $(@canvas).on 'mousedown', (e) =>
        action = this._event_to_action(e)
        @set_hover_node(undefined) # on click, un-hover
        this._notify(action.event, action.id) if action

      this._handle_hover()
      this._handle_drag()
      this._handle_mousewheel()

    _handle_hover: () ->
      $(@canvas).on 'mousemove', (e) =>
        px = @_event_to_px(e) # might be undefined
        @set_hover_node(px)
        e.preventDefault()

      $(@canvas).on 'mouseleave', (e) =>
        @set_hover_node(undefined)
        e.preventDefault()

    _handle_drag: () ->
      $(@canvas).on 'mousedown', (e) =>
        return if e.which != 1
        e.preventDefault()

        start_x = e.pageX
        zoom = @focus.get('zoom')
        start_pan = @focus.get('pan')
        width = $(@canvas).width()
        dragging = false # if we only move one pixel, that doesn't count

        update_from_event = (e) =>
          dx = e.pageX - start_x

          dragging ||= true if Math.abs(dx) > 3

          return if !dragging

          d_pan = (dx / width) * zoom

          this._notify('zoom-pan', { zoom: zoom, pan: start_pan - d_pan })

        $('body').append('<div id="focus-view-mousemove-handler"></div>')
        $(document).on 'mousemove.tree-view', (e) ->
          update_from_event(e)
          e.stopImmediatePropagation() # prevent normal hover operation
          e.preventDefault()

        $(document).on 'mouseup.tree-view', (e) =>
          update_from_event(e)
          $('#focus-view-mousemove-handler').remove()
          $(document).off('.tree-view')
          e.preventDefault()

    _handle_mousewheel: () ->
      # When the user moves mouse wheel in, we divide zoom by a factor of
      # mousewheel_zoom_factor. We adjust pan to whatever will keep the mouse
      # cursor pointing to the same location, in absolute terms.
      #
      # Before zoom, absolute location is pan1 + (cursor_fraction - 0.5) * zoom1
      # After, it's pan2 + (cursor_fraction - 0.5) * zoom2
      #
      # So pan2 = pan1 + (cursor_fraction - 0.5) * zoom1 - (cursor_fraction - 0.5) * zoom2
      $(@canvas).on 'mousewheel', (e) =>
        e.preventDefault()
        offset = $(@canvas).offset()
        x = e.pageX - offset.left
        width = $(@canvas).width()

        sign = e.deltaY > 0 && 1 || -1

        zoom1 = @focus.get('zoom')
        zoom2 = zoom1 * Math.pow(@options.mousewheel_zoom_factor, -sign)
        pan1 = @focus.get('pan')
        relative_cursor_fraction = ((x / width) - 0.5)

        pan2 = pan1 + relative_cursor_fraction * zoom1 - relative_cursor_fraction * zoom2

        this._notify('zoom-pan', { zoom: zoom2, pan: pan2 })

    _event_to_px: (e) ->
      offset = $(@canvas).offset()
      x = e.pageX - offset.left
      y = e.pageY - offset.top

      @last_draw?.pixel_to_node(x, y)

    _event_to_action: (e) ->
      return undefined if !@tree.root?

      offset = $(@canvas).offset()
      x = e.pageX - offset.left
      y = e.pageY - offset.top

      @last_draw?.pixel_to_action(x, y)

    _getColorLogic: ->
      taglike = @tree.state.get('taglike')
      if taglike?.name?
        { tagIds: [ taglike.id ], color: taglike.color || (new ColorTable()).get(taglike.name) }
      else if taglike?.query?
        { searchResultIds: [ taglike.id ], color: '#50ade5' }
      else
        null

    _redraw: () ->
      colorLogic = @_getColorLogic()
      highlightedNodeIds = TreeView.helpers.getHighlightedNodeIds(@state.get('selection'), @cache.on_demand_tree, @cache.document_store)

      @last_draw = new DrawOperation(@canvas, @tree, colorLogic, highlightedNodeIds, @hoverNodeId, @focus, @options)
      @last_draw.draw()

    update: () ->
      @tree.update()
      @focus.update(@tree)
      this._redraw()
      @_needs_update = @tree.needsUpdate() || @focus.needsUpdate()

    needs_update: () ->
      @_needs_update

    _set_needs_update: () ->
      if !@_needs_update
        @_needs_update = true
        this._notify('needs-update')

    _refresh_zoom_button_status: ->
      @zoomInButton.className = @focus.isZoomedInFully() && 'disabled' || ''
      @zoomOutButton.className = @focus.isZoomedOutFully() && 'disabled' || ''

    # Sets the node being hovered.
    #
    # We'll adjust @$hover_node_description to match.
    set_hover_node: (px) ->
      hoverNodeId = px?.json?.id
      return if hoverNodeId == @hoverNodeId
      @hoverNodeId = hoverNodeId

      if !px?
        @$hover_node_description.removeAttr('data-node-id')
        @$hover_node_description.hide()
      else
        json = px.json
        node_id_string = "#{json?.id}"

        return if @$hover_node_description.attr('data-node-id') == node_id_string

        # If we're here, we're hovering on a new node
        @$hover_node_description.hide()
        @$hover_node_description.empty()

        html = HOVER_NODE_TEMPLATE({ node: json })
        @$hover_node_description.append(html)
        @$hover_node_description.attr('data-node-id', node_id_string)
        @$hover_node_description.css({ opacity: 0.001 })
        @$hover_node_description.show() # Show it, so we can calculate dims

        h = @$hover_node_description.outerHeight(true)
        w = @$hover_node_description.outerWidth(true)

        $canvas = $(@canvas)
        offset = $canvas.offset()
        document_width = $(document).width()

        top = px.top - h
        left = px.hmid - w * 0.5

        if left + offset.left < 0
          left = 0
        if left + offset.left + w > document_width
          left = document_width - w - offset.left

        @$hover_node_description.css({
          left: left
          top: top
        })
        @$hover_node_description.animate({ opacity: 0.9 }, 'fast')

      @_set_needs_update()

  TreeView.helpers =
    # Returns a set of { nodeid: null }.
    #
    # A node is highlighted if we are viewing a document that is contained
    # in the node.
    getHighlightedNodeIds: (selection, onDemandTree, documentStore) ->
      documentNodes = {}

      # Parent nodes
      parentNodes = {}
      for nodeId in selection.nodes
        while (nodeId = onDemandTree.nodes[nodeId]?.parentId)?
          parentNodes[nodeId] = null

      # Selected documents
      for selectedDocumentId in selection.documents
        document = documentStore.documents[selectedDocumentId]
        if document?
          for nodeid in document.nodeids
            if nodeid not of parentNodes
              documentNodes[nodeid] = null

      documentNodes

  TreeView