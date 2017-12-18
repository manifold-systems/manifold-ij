class MyJsClass_Caret_MethodDeclaration {
  constructor() {
    this._foo = "later"
  }

  <caret>hi() {
  }

  get Yeah() {
    return this._foo
  }
  set Yeah( value ) {
    this._foo = value
  }
}