/*

    Copyright 2017 Loopring Project Ltd (Loopring Foundation).

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
pragma solidity 0.5.2;

/*

  Copyright 2017 Loopring Project Ltd (Loopring Foundation).

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/



/// @title Errors
contract Errors {
    string constant ZERO_VALUE                 = "ZERO_VALUE";
    string constant ZERO_ADDRESS               = "ZERO_ADDRESS";
    string constant INVALID_VALUE              = "INVALID_VALUE";
    string constant INVALID_ADDRESS            = "INVALID_ADDRESS";
    string constant INVALID_SIZE               = "INVALID_SIZE";
    string constant INVALID_SIG                = "INVALID_SIG";
    string constant INVALID_STATE              = "INVALID_STATE";
    string constant NOT_FOUND                  = "NOT_FOUND";
    string constant ALREADY_EXIST              = "ALREADY_EXIST";
    string constant REENTRY                    = "REENTRY";
    string constant UNAUTHORIZED               = "UNAUTHORIZED";
    string constant UNIMPLEMENTED              = "UNIMPLEMENTED";
    string constant UNSUPPORTED                = "UNSUPPORTED";
    string constant TRANSFER_FAILURE           = "TRANSFER_FAILURE";
    string constant WITHDRAWAL_FAILURE         = "WITHDRAWAL_FAILURE";
    string constant BURN_FAILURE               = "BURN_FAILURE";
    string constant BURN_RATE_FROZEN           = "BURN_RATE_FROZEN";
    string constant BURN_RATE_MINIMIZED        = "BURN_RATE_MINIMIZED";
    string constant UNAUTHORIZED_ONCHAIN_ORDER = "UNAUTHORIZED_ONCHAIN_ORDER";
    string constant INVALID_CANDIDATE          = "INVALID_CANDIDATE";
    string constant ALREADY_VOTED              = "ALREADY_VOTED";
    string constant NOT_OWNER                  = "NOT_OWNER";
}

/*

  Copyright 2017 Loopring Project Ltd (Loopring Foundation).

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/



/// @title ERC20 Token Interface
/// @dev see https://github.com/ethereum/EIPs/issues/20
/// @author Daniel Wang - <daniel@loopring.org>
contract ERC20 {
    function totalSupply()
        public
        view
        returns (uint256);

    function balanceOf(
        address who
        )
        public
        view
        returns (uint256);

    function allowance(
        address owner,
        address spender
        )
        public
        view
        returns (uint256);

    function transfer(
        address to,
        uint256 value
        )
        public
        returns (bool);

    function transferFrom(
        address from,
        address to,
        uint256 value
        )
        public
        returns (bool);

    function approve(
        address spender,
        uint256 value
        )
        public
        returns (bool);
}

/*

  Copyright 2017 Loopring Project Ltd (Loopring Foundation).

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/



/// @title Utility Functions for uint
/// @author Daniel Wang - <daniel@loopring.org>
library MathUint {

    function mul(
        uint a,
        uint b
        )
        internal
        pure
        returns (uint c)
    {
        c = a * b;
        require(a == 0 || c / a == b, "INVALID_VALUE");
    }

    function sub(
        uint a,
        uint b
        )
        internal
        pure
        returns (uint)
    {
        require(b <= a, "INVALID_VALUE");
        return a - b;
    }

    function add(
        uint a,
        uint b
        )
        internal
        pure
        returns (uint c)
    {
        c = a + b;
        require(c >= a, "INVALID_VALUE");
    }

    function hasRoundingError(
        uint value,
        uint numerator,
        uint denominator
        )
        internal
        pure
        returns (bool)
    {
        uint multiplied = mul(value, numerator);
        uint remainder = multiplied % denominator;
        // Return true if the rounding error is larger than 1%
        return mul(remainder, 100) > multiplied;
    }
}



/// @title ERC20 Token Implementation
/// @dev see https://github.com/ethereum/EIPs/issues/20
/// @author Daniel Wang - <daniel@loopring.org>
contract ERC20Token is ERC20, Errors{
    using MathUint for uint;

    string  public name;
    string  public symbol;
    uint8   public decimals;
    uint    public totalSupply_;

    mapping (address => uint256) balances;
    mapping (address => mapping (address => uint256)) internal allowed;

    event Transfer(
        address indexed from,
        address indexed to,
        uint256 value
    );

    event Approval(
        address indexed owner,
        address indexed spender,
        uint256 value
    );

    constructor(
        string memory _name,
        string memory _symbol,
        uint8         _decimals,
        uint          _totalSupply,
        address       _firstHolder
        )
        public
    {
        require(_totalSupply > 0, "INVALID_VALUE");
        require(_firstHolder != address(0x0), "ZERO_ADDRESS");
        checkSymbolAndName(_symbol,_name);

        name = _name;
        symbol = _symbol;
        decimals = _decimals;
        totalSupply_ = _totalSupply;

        balances[_firstHolder] = totalSupply_;
    }

    function ()
        external
        payable
    {
        revert(UNSUPPORTED);
    }

    /**
    * @dev total number of tokens in existence
    */
    function totalSupply()
        public
        view
        returns (uint256)
    {
        return totalSupply_;
    }

    /**
    * @dev transfer token for a specified address
    * @param _to The address to transfer to.
    * @param _value The amount to be transferred.
    */
    function transfer(
        address _to,
        uint256 _value
        )
        public
        returns (bool)
    {
        require(_to != address(0), "ZERO_ADDRESS");
        require(_value <= balances[msg.sender], "INVALID_VALUE");

        // SafeMath.sub will throw if there is not enough balance.
        balances[msg.sender] = balances[msg.sender].sub(_value);
        balances[_to] = balances[_to].add(_value);
        emit Transfer(msg.sender, _to, _value);
        return true;
    }

    /**
    * @dev Gets the balance of the specified address.
    * @param _owner The address to query the the balance of.
    * @return An uint256 representing the amount owned by the passed address.
    */
    function balanceOf(
        address _owner
        )
        public
        view
        returns (uint256 balance)
    {
        return balances[_owner];
    }

    /**
     * @dev Transfer tokens from one address to another
     * @param _from address The address which you want to send tokens from
     * @param _to address The address which you want to transfer to
     * @param _value uint256 the amount of tokens to be transferred
     */
    function transferFrom(
        address _from,
        address _to,
        uint256 _value
        )
        public
        returns (bool)
    {
        require(_to != address(0), "ZERO_ADDRESS");
        require(_value <= balances[_from], "INVALID_VALUE");
        require(_value <= allowed[_from][msg.sender], "INVALID_VALUE");

        balances[_from] = balances[_from].sub(_value);
        balances[_to] = balances[_to].add(_value);
        allowed[_from][msg.sender] = allowed[_from][msg.sender].sub(_value);
        emit Transfer(_from, _to, _value);
        return true;
    }

    /**
     * @dev Approve the passed address to spend the specified amount of tokens on behalf of msg.sender.
     *
     * Beware that changing an allowance with this method brings the risk that someone may use both the old
     * and the new allowance by unfortunate transaction ordering. One possible solution to mitigate this
     * race condition is to first reduce the spender's allowance to 0 and set the desired value afterwards:
     * https://github.com/ethereum/EIPs/issues/20#issuecomment-263524729
     * @param _spender The address which will spend the funds.
     * @param _value The amount of tokens to be spent.
     */
    function approve(
        address _spender,
        uint256 _value
        )
        public
        returns (bool)
    {
        allowed[msg.sender][_spender] = _value;
        emit Approval(msg.sender, _spender, _value);
        return true;
    }

    /**
     * @dev Function to check the amount of tokens that an owner allowed to a spender.
     * @param _owner address The address which owns the funds.
     * @param _spender address The address which will spend the funds.
     * @return A uint256 specifying the amount of tokens still available for the spender.
     */
    function allowance(
        address _owner,
        address _spender
        )
        public
        view
        returns (uint256)
    {
        return allowed[_owner][_spender];
    }

    /**
     * @dev Increase the amount of tokens that an owner allowed to a spender.
     *
     * approve should be called when allowed[_spender] == 0. To increment
     * allowed value is better to use this function to avoid 2 calls (and wait until
     * the first transaction is mined)
     * From MonolithDAO Token.sol
     * @param _spender The address which will spend the funds.
     * @param _addedValue The amount of tokens to increase the allowance by.
     */
    function increaseApproval(
        address _spender,
        uint _addedValue
        )
        public
        returns (bool)
    {
        allowed[msg.sender][_spender] = allowed[msg.sender][_spender].add(_addedValue);
        emit Approval(msg.sender, _spender, allowed[msg.sender][_spender]);
        return true;
    }

    /**
     * @dev Decrease the amount of tokens that an owner allowed to a spender.
     *
     * approve should be called when allowed[_spender] == 0. To decrement
     * allowed value is better to use this function to avoid 2 calls (and wait until
     * the first transaction is mined)
     * From MonolithDAO Token.sol
     * @param _spender The address which will spend the funds.
     * @param _subtractedValue The amount of tokens to decrease the allowance by.
     */
    function decreaseApproval(
        address _spender,
        uint _subtractedValue
        )
        public
        returns (bool)
    {
        uint oldValue = allowed[msg.sender][_spender];
        if (_subtractedValue > oldValue) {
            allowed[msg.sender][_spender] = 0;
        } else {
            allowed[msg.sender][_spender] = oldValue.sub(_subtractedValue);
        }
        emit Approval(msg.sender, _spender, allowed[msg.sender][_spender]);
        return true;
    }

    // Make sure symbol has 3-8 chars in [A-Za-z._] and name has up to 128 chars.
    function checkSymbolAndName(
        string memory _symbol,
        string memory _name
        )
        internal
        pure
    {
        bytes memory s = bytes(_symbol);
        require(s.length >= 3 && s.length <= 8, "INVALID_SIZE");
        for (uint i = 0; i < s.length; i++) {
            // make sure symbol contains only [A-Za-z._]
            require(
                s[i] == 0x2E || (
                s[i] == 0x5F) || (
                s[i] >= 0x41 && s[i] <= 0x5A) || (
                s[i] >= 0x61 && s[i] <= 0x7A), "INVALID_VALUE");
        }
        bytes memory n = bytes(_name);
        require(n.length >= s.length && n.length <= 128, "INVALID_SIZE");
        for (uint i = 0; i < n.length; i++) {
            require(n[i] >= 0x20 && n[i] <= 0x7E, "INVALID_VALUE");
        }
    }
}
