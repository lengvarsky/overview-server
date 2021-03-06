define [
  'underscore'
  'backbone'
  'apps/Tree/controllers/TreeController'
], (_, Backbone, TreeController) ->
  describe 'apps/Tree/controllers/TreeController', ->
    class MockState extends Backbone.Model

    class Params
      constructor: (@stuff) ->
        @node = @stuff.node
        @reset =
          byNode: (node) -> new Params(node: node)

      equals: (rhs) ->
        _.isEqual(@, rhs)

    beforeEach ->
      @params = new Params({})

      @state = new MockState
        document: 'document'
        documentListParams: @params
        oneDocumentSelected: false
      @state.resetDocumentListParams = =>
        byNode: (node) =>
          @state.set(documentListParams: new Params(node: node))

      @focus =
        animateNode: sinon.spy()
        animatePanAndZoom: sinon.spy()
        setPanAndZoom: sinon.spy()
      @tree =
        unloadNodeChildren: sinon.spy()
        demandNode: sinon.spy()
        getRoot: sinon.stub()
        getNode: sinon.stub()
        id_tree:
          root: null
          children: {}
          parent: {}
          is_id_ancestor_of_id: sinon.stub().returns(false)
      @view =
        needsUpdate: sinon.stub()
        update: sinon.spy()
        setHoverNode: sinon.spy()
        nodeid_below: sinon.stub()
        nodeid_above: sinon.stub()
        nodeid_left: sinon.stub()
        nodeid_right: sinon.stub()

      _.extend(@view, Backbone.Events)

    afterEach ->
      @subject?.stopListening()

    describe 'when it needs an update on init', ->
      beforeEach (done) ->
        @view.needsUpdate.returns(true)

        @subject = new TreeController
          state: @state
          focus: @focus
          tree: @tree
          view: @view
          requestAnimationFrame: _.defer
        _.defer(done)

      it 'should update', ->
        expect(@view.update).to.have.been.called

      it 'should keep updating until needsUpdate is false', (done) ->
        _.defer =>
          expect(@view.update).to.have.been.calledTwice
          @view.needsUpdate.returns(false)
          _.defer =>
            expect(@view.update).not.to.have.been.calledThrice
            done()

      it 'should not double-animate if the view triggers needs-update', (done) ->
        _.defer =>
          expect(@view.update).to.have.been.calledTwice
          @view.trigger('needs-update')
          _.defer =>
            expect(@view.update).to.have.callCount(3)
            @view.needsUpdate.returns(false) # avoid leaking
            done()

    describe 'normally', ->
      beforeEach ->
        @subject = new TreeController
          state: @state
          focus: @focus
          tree: @tree
          view: @view
          requestAnimationFrame: (f) -> window.setTimeout(f, 1)

        @selectedNodeId = =>
          params = @state.get('documentListParams')
          stuff = params.stuff
          throw "No stuff in params #{JSON.stringify(params)}" if !stuff?
          stuff.node?.id

      it 'should not update when it does not need an update on init', (done) ->
        _.defer =>
          expect(@view.update).not.to.have.been.called
          done()

      it 'should animate from when the tree triggers needs-update until needsUpdate returns false', (done) ->
        @view.needsUpdate.returns(true)
        @view.trigger('needs-update')
        _.defer =>
          expect(@view.update).to.have.been.called
          _.defer =>
            expect(@view.update).to.have.been.calledTwice
            @view.needsUpdate.returns(false)
            _.defer =>
              expect(@view.update).not.to.have.been.calledThrice
              done()

      it 'should select nothing when navigating with no tree', ->
        @params.reset.byNode = sinon.spy()
        expect(=>
          @subject.goDown()
          @subject.goUp()
          @subject.goLeft()
          @subject.goRight()
        ).not.to.throw
        expect(@params.reset.byNode).not.to.have.been.called

      describe 'when navigating Down from no selection', ->
        beforeEach ->
          @tree.id_tree.root = 1
          @tree.getRoot.returns(id: 1)
          @subject.goDown()

        it 'should select the root node', -> expect(@selectedNodeId()).to.eq(1)
        it 'should expand the root node', -> expect(@tree.demandNode).to.have.been.calledWith(1)

      describe 'when navigating Down from a node', ->
        beforeEach ->
          @tree.id_tree.root = 1
          @view.nodeid_below.withArgs(1).returns(2)
          @tree.id_tree.children[1] = [ 2, 3, 4 ]
          @tree.getNode.withArgs(2).returns(id: 2)
          @state.set(documentListParams: new Params(node: { id: 1 }))
          @subject.goDown()

        it 'should select the node', -> expect(@selectedNodeId()).to.eq(2)
        it 'should expand the node', -> expect(@tree.demandNode).to.have.been.calledWith(2)

      describe 'when navigating Up from a node', ->
        beforeEach ->
          @tree.id_tree.root = 1
          @tree.id_tree.parent[2] = 1
          @view.nodeid_above.withArgs(2).returns(1)
          @tree.getNode.withArgs(1).returns(id: 1)
          @state.set(documentListParams: new Params(node: { id: 2 }))
          @subject.goUp()

        it 'should select the parent node', -> expect(@selectedNodeId()).to.eq(1)

      describe 'when navigating left and right', ->
        beforeEach ->
          @tree.id_tree.root = 1
          @tree.id_tree.parent[3] = 1
          @tree.id_tree.children[1] = [ 2, 3, 4 ]
          @view.nodeid_left.withArgs(3).returns(2)
          @view.nodeid_left.withArgs(2).returns(null)
          @view.nodeid_right.withArgs(3).returns(4)
          @view.nodeid_right.withArgs(4).returns(null)
          @tree.getNode.withArgs(2).returns(id: 2)
          @tree.getNode.withArgs(4).returns(id: 4)
          @state.set(documentListParams: new Params(node: { id: 3 }))

        it 'should go Right', ->
          @subject.goRight()
          expect(@selectedNodeId()).to.eq(4)
          expect(@tree.demandNode).to.have.been.calledWith(4)

        it 'should go Left', ->
          @subject.goLeft()
          expect(@selectedNodeId()).to.eq(2)
          expect(@tree.demandNode).to.have.been.calledWith(2)

        it 'should not go too far Right', ->
          @subject.goRight(); @subject.goRight()
          expect(@selectedNodeId()).to.eq(4)

        it 'should not go too far Left', ->
          @subject.goLeft(); @subject.goLeft()
          expect(@selectedNodeId()).to.eq(2)

      describe 'on expand', ->
        beforeEach ->
          @node = { id: 3, description: 'description' }
          @view.trigger('expand', @node)

        it 'should expand the node', -> expect(@tree.demandNode).to.have.been.calledWith(@node.id)

      describe 'on collapse', ->
        beforeEach ->
          @node = { id: 3, description: 'description' }

        it 'should unload the node children', ->
          @view.trigger('collapse', @node)
          expect(@tree.unloadNodeChildren).to.have.been.calledWith(@node.id)

        it 'should change the document list params, normally', ->
          @state.on('change:documentListParams', spy = sinon.spy())
          @view.trigger('collapse', @node)
          expect(spy).not.to.have.been.called

        it 'should change the document list params if a node is being unloaded', ->
          @params.node = { id: 6 }
          @params.reset.byNode = (node) -> { node: node }
          @tree.id_tree.is_id_ancestor_of_id.returns(true)

          @view.trigger('collapse', @node)

          expect(@tree.id_tree.is_id_ancestor_of_id).to.have.been.calledWith(3, 6)
          expect(@state.get('documentListParams').node).to.eq(@node)

      describe 'when clicking a node', ->
        beforeEach ->
          @node = { id: 3, description: 'description' }

        it 'should expand the node when its children are undefined', ->
          @view.trigger('click', @node)
          expect(@tree.demandNode).to.have.been.calledWith(@node.id)

        it 'should deselect a selected document when clicking on a selected node', ->
          params1 =
            equals: (x) -> x == params1
            reset:
              byNode: -> params1
          @state.set
            documentListParams: params1
            document: 'foo'
            oneDocumentSelected: true
          @view.trigger('click', @node)
          expect(@state.get('document')).to.be.null
          expect(@state.get('oneDocumentSelected')).to.be.false

        it 'should change parameters when changing nodes', ->
          params1 =
            reset:
              byNode: -> { foo: 'bar', equals: -> false }
          @state.set
            documentListParams: params1
            document: 'foo'
            oneDocumentSelected: true
          @view.trigger('click', @node)
          expect(@state.get('document')).to.be.null
          expect(@state.get('documentListParams').foo).to.eq('bar')
          expect(@state.get('oneDocumentSelected')).to.be.true
