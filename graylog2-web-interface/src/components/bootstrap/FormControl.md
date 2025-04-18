```js
import { FormGroup, ControlLabel, HelpBlock } from 'components/bootstrap';

class FormExample extends React.Component {
  constructor(props, context) {
    super(props, context);

    this.handleChange = this.handleChange.bind(this);

    this.state = {
      value: '',
    };
  }

  getValidationState() {
    const length = this.state.value.length;
    if (length > 10) return 'success';
    if (length > 5) return 'warning';
    if (length > 0) return 'error';
    return null;
  }

  handleChange(e) {
    this.setState({ value: e.target.value });
  }

  render() {
    return (
      <form>
        <FormGroup controlId="formBasicText" validationState={this.getValidationState()}>
          <ControlLabel>Working example with validation</ControlLabel>
          <FormControl type="text" value={this.state.value} placeholder="Enter text" onChange={this.handleChange} />
          <FormControl.Feedback />
          <HelpBlock>Validation is based on string length.</HelpBlock>
        </FormGroup>
      </form>
    );
  }
}

<FormExample />;
```
